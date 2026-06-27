package io.ddd4j.web.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端聚合API
 *
 * @author Loong Wan
 * @公众号 PartMe.AI
 */
@Slf4j
@RestController
@RequestMapping("/client")
public class ClientAggregateController implements AggregateController {

}