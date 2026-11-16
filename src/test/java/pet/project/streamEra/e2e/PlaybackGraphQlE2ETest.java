package pet.project.streamEra.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pet.project.streamEra.videoDomain.entity.Category;
import pet.project.streamEra.videoDomain.entity.Video;
import pet.project.streamEra.videoDomain.entity.VideoStatus;
import pet.project.streamEra.videoDomain.repository.CategoryRepository;
import pet.project.streamEra.videoDomain.repository.VideoRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * END-TO-END (E2E) test for the playback GraphQL API.
 *
 * <p>Difference from {@code PlaybackGraphQlIntegrationTest}: there, the
 * GraphQL document was executed in-process. Here the application is started on
 * a real random port and requests travel over a real HTTP connection:</p>
 *
 * <pre>
 *   HttpGraphQlTester (HTTP client)
 *        -> Tomcat (real server, random port)
 *        -> Spring Security filter chain (JWT filter, permitAll rules)
 *        -> Spring MVC GraphQL endpoint (/graphql)
 *        -> PlaybackController -> PlaybackService -> VideoRepository
 *        -> PostgreSQL container
 * </pre>
 *
 * <p>This is the test that would catch problems the in-process test cannot:
 * wrong endpoint path, security blocking /graphql, JSON request/response
 * serialization, servlet wiring, port configuration, etc.</p>
 *
 * <p>Why not make EVERY test E2E? Because HTTP + a real server is slower and
 * makes failures less precise. Use the cheapest test that can fail for the
 * reason you care about: unit for business rules, in-process integration for
 * wiring, E2E for the public protocol.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@ActiveProfiles("test")
@Testcontainers
class PlaybackGraphQlE2ETest {

    /**
     * Its own container: this class uses a different Spring context (real web
     * server) and must not share mutable state with the integration test.
     * A shared container is possible, but only worth the extra plumbing when
     * startup time becomes a real problem.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    /**
     * Unlike GraphQlTester, HttpGraphQlTester sends real HTTP requests to the
     * random port the application booted on. It still gives us the nice
     * path-based GraphQL assertions.
     */
    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * E2E tests must clean up in committed state, not in a rolled-back test
     * transaction. The HTTP request runs on a server thread with its own
     * transaction, so it would never see uncommitted rows.
     *
     * <p>Why delete instead of relying on unique names? A committed database
     * accumulates state across tests and across test classes. Starting each
     * E2E test from an empty video catalog makes assertions exact and avoids
     * order-dependent failures.</p>
     */
    @BeforeEach
    void resetCatalog() {
        // video_categories is not managed by Hibernate deletion here, so clear
        // the join rows explicitly before removing the videos.
        jdbcTemplate.update("DELETE FROM video_categories");
        videoRepository.deleteAll();
    }

    @Test
    @DisplayName("HTTP POST /graphql: anonymous client can list playable videos")
    void graphqlOverHttp_listsPlayableVideosWithoutAuthentication() {
        seed("E2E Ready", VideoStatus.OK, "Action");
        seed("E2E Pending", VideoStatus.PENDING, "Drama");
        seed("E2E Processing", VideoStatus.PROCESSING);

        // No Authorization header is set. This proves two things at once:
        //  1. SecurityConfig really permits /graphql.
        //  2. The JwtAuthFilter passes anonymous requests through instead of
        //     rejecting a missing token.
        graphQlTester.document("""
                        query {
                            videos {
                                name
                                videoStatus
                                categorySet { name }
                            }
                        }
                        """)
                .execute()
                .path("videos[*].name")
                .entityList(String.class)
                .satisfies(names -> assertThat(names)
                        .containsExactlyInAnyOrder("E2E Ready", "E2E Pending"));

        // The non-playable row exists, so the assertion above is meaningful.
        assertThat(videoRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("HTTP POST /graphql: video_byId returns nested data")
    void graphqlOverHttp_returnsVideoByIdWithCategories() {
        Video video = seed("E2E By Id", VideoStatus.OK, "Action", "Sci-Fi");

        graphQlTester.document("""
                        query GetVideo($id: Long!) {
                            video_byId(videoId: $id) {
                                videoId
                                name
                                videoUrl
                                categorySet { name }
                            }
                        }
                        """)
                .variable("id", video.getVideoId())
                .execute()
                .path("video_byId.videoId")
                .entity(Long.class)
                .isEqualTo(video.getVideoId());

        graphQlTester.document("""
                        query GetVideo($id: Long!) {
                            video_byId(videoId: $id) {
                                categorySet { name }
                            }
                        }
                        """)
                .variable("id", video.getVideoId())
                .execute()
                .path("video_byId.categorySet[*].name")
                .entityList(String.class)
                .satisfies(names -> assertThat(names)
                        .containsExactlyInAnyOrder("Action", "Sci-Fi"));
    }

    @Test
    @DisplayName("HTTP POST /graphql: a missing id comes back as a GraphQL error payload")
    void graphqlOverHttp_reportsMissingVideoAsGraphQlError() {
        // GraphQL convention: transport is 200 OK, failures live in "errors".
        // HttpGraphQlTester's errors() lets us assert that without parsing JSON
        // by hand. If this ever becomes an HTTP 500, clients break; this test
        // is the place where that regression would show up.
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

    /**
     * E2E fixtures are written directly through the repository.
     *
     * <p>Why not create the video through the GraphQL mutation? Because
     * {@code add_video} needs S3/MinIO and RabbitMQ; that would turn a playback
     * test into an upload-infrastructure test. For the playback feature, the
     * "arrange" step may use the cheapest reliable channel. The "act" step is
     * still the real public HTTP API, which is the part under test.</p>
     */
    private Video seed(String name, VideoStatus status, String... categoryNames) {
        Video video = new Video();
        video.setName(name);
        video.setVideoLength(90L);
        video.setSize(2_097_152L);
        video.setVideoUrl("/media/" + name.toLowerCase().replace(' ', '-') + ".mp4");
        video.setVideoStatus(status);
        video = videoRepository.saveAndFlush(video);

        // See PlaybackGraphQlIntegrationTest for why these links are inserted
        // with SQL: the entity does not map the join table's surrogate key.
        for (String categoryName : categoryNames) {
            Category category = categoryRepository.findByName(categoryName).orElseThrow();
            jdbcTemplate.update("""
                    INSERT INTO video_categories (video_categories_id, video_id, category_id)
                    VALUES (gen_random_uuid(), ?, ?)
                    """, video.getVideoId(), category.getCategoryId());
        }
        return video;
    }
}
