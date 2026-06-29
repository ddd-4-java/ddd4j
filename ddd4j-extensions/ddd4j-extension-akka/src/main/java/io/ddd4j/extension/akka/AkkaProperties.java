/**
 * Copyright (C) 2018 Hiwepy (http://hiwepy.io).
 * All Rights Reserved.
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */

package io.ddd4j.extension.akka;

import lombok.Data;

/**
 * Akka Properties
 */
@Data
// @ConfigurationProperties(prefix = "ddd4j.akka")
public class AkkaProperties {

    /**
     * Actor system name
     */
    private String name = "ddd4j-akka-system";

    /**
     * Enable akka auto configuration
     */
    private boolean enabled = true;

}

