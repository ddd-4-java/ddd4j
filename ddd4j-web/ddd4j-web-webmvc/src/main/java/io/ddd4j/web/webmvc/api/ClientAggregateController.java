package io.ddd4j.web.webmvc.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端聚合API
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RestController
@RequestMapping("/client")
public class ClientAggregateController implements AggregateController {

}