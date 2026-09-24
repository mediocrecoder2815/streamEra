package pet.project.streamEra.graphql.config;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Configuration
public class GraphQlConfig {

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final Coercing<OffsetDateTime, String> DELEGATE =
            (Coercing<OffsetDateTime, String>) ExtendedScalars.DateTime.getCoercing();

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder.scalar(dateScalar());
    }

    @Bean
    public GraphQLScalarType dateScalar() {
        return GraphQLScalarType.newScalar()
                .name("Date")
                .coercing(new Coercing<Instant, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult, GraphQLContext graphQLContext, Locale locale)
                            throws CoercingSerializeException {
                        if (dataFetcherResult instanceof Instant instant) {
                            dataFetcherResult = instant.atOffset(ZoneOffset.UTC);
                        }
                        return DELEGATE.serialize(dataFetcherResult, graphQLContext, locale);
                    }

                    @Override
                    public Instant parseValue(Object input, GraphQLContext graphQLContext, Locale locale)
                            throws CoercingParseValueException {
                        return DELEGATE.parseValue(input, graphQLContext, locale).toInstant();
                    }

                    @Override
                    public Instant parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext,
                                                Locale locale) throws CoercingParseLiteralException {
                        return DELEGATE.parseLiteral(input, variables, graphQLContext, locale).toInstant();
                    }

                    @Override
                    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
                        if (input instanceof Instant instant) {
                            input = instant.atOffset(ZoneOffset.UTC);
                        }
                        return DELEGATE.valueToLiteral(input, graphQLContext, locale);
                    }
                })
                .build();
    }
}