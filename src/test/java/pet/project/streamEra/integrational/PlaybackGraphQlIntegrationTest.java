package pet.project.streamEra.integrational;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import pet.project.streamEra.videoDomain.entity.Category;
import pet.project.streamEra.videoDomain.entity.Video;
import pet.project.streamEra.videoDomain.entity.VideoStatus;
import pet.project.streamEra.videoDomain.repository.CategoryRepository;
import pet.project.streamEra.videoDomain.repository.VideoRepository;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INTEGRATION test for the playback read path.
 *
 * <p>What "integration" means here: we start the real Spring application
 * context and let one request travel through the real layers:</p>
 *
 * <pre>
 *   GraphQL document
 *        -> Spring GraphQL request execution
 *        -> PlaybackController        (schema binding, @Argument)
 *        -> PlaybackService           (business rules / filtering)
 *        -> VideoRepository           (Spring Data JPA + @EntityGraph)
 *        -> Hibernate                 (mapping, SQL)
 *        -> real PostgreSQL           (schema created by Flyway)
 * </pre>
 *
 * <p>What is NOT mocked: database, JPA, GraphQL schema, controller or service.
 * Only the entry point is in-process (GraphQlTester) instead of a real socket.
 * The sibling {@code PlaybackGraphQlE2ETest} covers the HTTP/socket entry point.</p>
 *
 * <p>Why Testcontainers instead of the docker-compose database?</p>
 * <ul>
 *   <li>The database is created for this test run and destroyed afterwards,
 *       so results do not depend on whatever is in your local database.</li>
 *   <li>Migrations run from scratch on every CI run, which catches broken
 *       Flyway scripts — something an already-migrated dev database hides.</li>
 *   <li>No manual setup ("docker compose up") before running tests.</li>
 * </ul>
 * The cost is that the first test run pulls the PostgreSQL image and each
 * test JVM starts a container. That is the usual trade-off for correctness.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureGraphQlTester
@Testcontainers
@Transactional
class PlaybackGraphQlIntegrationTest {

    /**
     * A real PostgreSQL container.
     *
     * <p>{@code static} = one container per test class, not per test method.
     * Starting a database is expensive (seconds), so doing it per method would
     * make the suite needlessly slow.</p>
     *
     * <p>{@code @ServiceConnection} is the modern Spring Boot 4 way to wire a
     * container into the application: Spring reads the container's JDBC URL,
     * username and password and registers them as {@code spring.datasource.*}.
     * Without it you would need a {@code @DynamicPropertySource} method and
     * manually copy four properties. {@code @ServiceConnection} also knows
     * which database type it is talking to, so no duplicated "postgres" string.</p>
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    /**
     * The tester is pre-configured with the application's GraphQL schema and
     * executes documents in-process. No HTTP, no authentication filter.
     */
    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    /**
     * The ManyToMany join table has a surrogate primary key column
     * ({@code video_categories_id}) that the entity does not map. Hibernate's
     * join-table INSERT therefore omits it, and the database rejects the row.
     *
     * <p>We insert link rows with SQL for now so this playback test stays
     * focused. This is exactly the kind of defect an integration test finds and
     * a mocked-repository unit test cannot. The real fix is either a migration
     * that gives the column a default, or mapping the join table as an entity.</p>
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Clears the JPA first-level cache between seed and query, so the GraphQL
     * read really goes to PostgreSQL (like a fresh request does) instead of
     * returning our partially-built fixture objects from the persistence
     * context.
     */
    @Autowired
    private EntityManager entityManager;

    private Video readyVideo;
    private Video pendingVideo;
    private Video processingVideo;

    /**
     * Seed exactly the data this test class needs.
     *
     * <p>Why not rely on the rows from Flyway? Because a test should own its
     * data: if someone edits a migration, the test still describes its own
     * scenario. Flyway's job here is the SCHEMA, not the fixtures.</p>
     *
     * <p>Why insert with the repository instead of an SQL script? The entity
     * mapping is part of what we are integrating. Using the repository also
     * makes the setup readable and type-safe. An SQL script is better when you
     * specifically want to test raw SQL or bypass JPA.</p>
     */
    @BeforeEach
    void seedPlaybackData() {
        // Categories already exist because V3__mock_data.sql seeds them.
        // We look them up by name to avoid depending on generated ids.
        Category action = categoryRepository.findByName("Action").orElseThrow();
        Category drama = categoryRepository.findByName("Drama").orElseThrow();

        readyVideo = videoRepository.saveAndFlush(video("Integration Ready", VideoStatus.OK));
        pendingVideo = videoRepository.saveAndFlush(video("Integration Pending", VideoStatus.PENDING));

        linkCategory(readyVideo, action);
        linkCategory(readyVideo, drama);
        linkCategory(pendingVideo, action);

        // These two must never be visible through playback.
        processingVideo = videoRepository.saveAndFlush(video("Integration Processing", VideoStatus.PROCESSING));
        videoRepository.saveAndFlush(video("Integration Error", VideoStatus.ERROR));

        // Forget the seed entities. The next read must load from the database,
        // including categorySet through VideoRepository's @EntityGraph.
        entityManager.clear();
    }

    @Test
    @DisplayName("videos: returns OK and PENDING videos, but hides PROCESSING/ERROR")
    void videos_returnsOnlyPlayableVideos() {
        // The triple-quoted string is a GraphQL document. Keeping it inline
        // makes the test self-contained; move it to a .graphql file only when
        // the same document is reused by many tests.
        GraphQlTester.Response response = graphQlTester.document("""
                        query {
                            videos {
                                name
                                videoStatus
                            }
                        }
                        """)
                .execute();

        // Assert on the returned GraphQL payload, not on service internals.
        // This is the contract clients actually depend on.
        assertThat(response.path("videos[*].name").entityList(String.class).get())
                .containsExactlyInAnyOrder("Integration Ready", "Integration Pending");

        assertThat(response.path("videos[*].videoStatus").entityList(String.class).get())
                .containsExactlyInAnyOrder("OK", "PENDING");

        // Guard against the filter silently disappearing: the two
        // non-playable rows exist in the database right now.
        assertThat(videoRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("video_byId: returns the full video, including its categories")
    void videoById_returnsVideoWithCategories() {
        // Using a GraphQL variable instead of string-concatenating the id is
        // the same technique clients use. It also exercises the custom Long
        // scalar declared in GraphQlConfig.
        graphQlTester.document("""
                        query GetVideo($id: Long!) {
                            video_byId(videoId: $id) {
                                videoId
                                name
                                videoLength
                                videoStatus
                                categorySet {
                                    name
                                }
                            }
                        }
                        """)
                .variable("id", readyVideo.getVideoId())
                .execute()
                .path("video_byId.videoId")
                .entity(Long.class)
                .isEqualTo(readyVideo.getVideoId());

        graphQlTester.document("""
                        query GetVideo($id: Long!) {
                            video_byId(videoId: $id) {
                                name
                                categorySet { name }
                            }
                        }
                        """)
                .variable("id", readyVideo.getVideoId())
                .execute()
                .path("video_byId.categorySet[*].name")
                .entityList(String.class)
                .satisfies(names -> assertThat(names)
                        .containsExactlyInAnyOrder("Action", "Drama"));
    }

    @Test
    @DisplayName("video_byName: returns a PENDING video by name")
    void videoByName_returnsPendingVideo() {
        // PENDING is intentionally playable while the worker is still
        // processing the upload. This test pins that business rule.
        graphQlTester.document("""
                        query GetVideo($name: String) {
                            video_byName(name: $name) {
                                name
                                videoStatus
                            }
                        }
                        """)
                .variable("name", pendingVideo.getName())
                .execute()
                .path("video_byName.name")
                .entity(String.class)
                .isEqualTo("Integration Pending");
    }

    @Test
    @DisplayName("video_byId: missing id is reported as a GraphQL error, not an empty video")
    void videoById_unknownIdProducesGraphQlError() {
        // PlaybackService throws EntityNotFoundException. Without a
        // DataFetcherExceptionResolver, Spring GraphQL turns the exception into
        // an entry in the "errors" array and keeps the process healthy.
        //
        // We assert only on the shape (an error exists and belongs to this
        // field), not on the exact English message: messages are framework
        // details and change between versions.
        graphQlTester.document("""
                        query GetVideo($id: Long!) {
                            video_byId(videoId: $id) { name }
                        }
                        """)
                .variable("id", 9_999_999L)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getPath()).isEqualTo("video_byId");
                });
    }

    @Test
    @DisplayName("video_byId: a non-playable video surfaces a GraphQL error (current behaviour)")
    void videoById_nonPlayableVideoDocumentsCurrentBehaviour() {
        // This test documents an existing design wart rather than endorsing it:
        //  - PlaybackService returns null for PROCESSING/ERROR videos
        //  - the schema declares video_byId as Video! (non-null)
        // So a client asking for a valid-but-not-yet-playable video gets a
        // GraphQL error instead of "null" or a typed "not playable" state.
        //
        // Why keep such a test? Integration tests should pin down what the
        // system really does. If the team decides to change the contract
        // (e.g. make video_byId nullable), this test fails on purpose and
        // forces a conscious decision. Unit tests would not catch it because
        // the conflict is between the schema and the service.
        graphQlTester.document("""
                        query GetVideo($id: Long!) {
                            video_byId(videoId: $id) { name }
                        }
                        """)
                .variable("id", processingVideo.getVideoId())
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).isNotEmpty();
                    assertThat(errors.get(0).getPath()).isEqualTo("video_byId");
                });
    }

    private static Video video(String name, VideoStatus status) {
        Video video = new Video();
        video.setName(name);
        // The V1 migration declares these columns NOT NULL, so every fixture
        // must provide them. This is exactly the kind of database constraint a
        // pure unit test with a mocked repository would never reveal.
        video.setVideoLength(120L);
        video.setSize(1_048_576L);
        video.setVideoUrl("/media/" + name.toLowerCase().replace(' ', '-') + ".mp4");
        video.setVideoStatus(status);
        return video;
    }

    private void linkCategory(Video video, Category category) {
        jdbcTemplate.update("""
                INSERT INTO video_categories (video_categories_id, video_id, category_id)
                VALUES (gen_random_uuid(), ?, ?)
                """, video.getVideoId(), category.getCategoryId());
    }
}
