package com.portability.adapters.out.ids;

import com.portability.domain.ports.out.IdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Generates identifiers at the boundary; the application only asks for "the next id". */
@Component
public class UuidIdGenerator implements IdGenerator {

    @Override
    public String next() {
        return UUID.randomUUID().toString();
    }
}
