package io.ddd4j.core.domain.model;

import java.io.Serializable;

/**
 * DDD value object marker.
 *
 * <p>Value objects should be immutable and compared by their attributes. They do
 * not have a lifecycle identity and should not be persisted through repository
 * APIs directly.</p>
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 * @since 2.0.x
 */
public interface ValueObject extends Serializable {
}
