package com.example.backend.architecture;

import com.example.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithVerifierTest {

    @Test
    void verifyModuleBoundaries() {
        ApplicationModules.of(BackendApplication.class).verify();
    }
}
