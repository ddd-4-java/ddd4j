package io.ddd4j.extension.validation;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidationServiceCopyContractTest {

    @Test
    void rejectsNullCheckerWhenConstructed() {
        assertThatThrownBy(() -> new FileValidationService(file -> Optional.empty(),
                Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void acceptsAbsentCheckerCollection() {
        assertThatCode(() -> new FileValidationService(file -> Optional.empty(), null))
                .doesNotThrowAnyException();
    }
}
