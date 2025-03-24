package de.rptu.cs.exclaim.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JacksonBeans {
    public static final ObjectWriter OBJECT_WRITER;
    public static final ObjectReader OBJECT_READER;

    static {
        ObjectMapper objectMapper = new ObjectMapper();
        OBJECT_WRITER = objectMapper.writer();
        OBJECT_READER = objectMapper.reader();
    }

    @Bean
    public ObjectWriter objectWriter() {
        return OBJECT_WRITER;
    }

    @Bean
    public ObjectReader objectReader() {
        return OBJECT_READER;
    }
}
