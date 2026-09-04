package io.ddd4j.web.webmvc.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端聚合API
 *
 * @author <a href="https://github.com/partme-ai">PartMe.AI</a>
 */
@Slf4j
@RestController
@RequestMapping("/admin")
public class AdminAggregateController implements AggregateController {

}