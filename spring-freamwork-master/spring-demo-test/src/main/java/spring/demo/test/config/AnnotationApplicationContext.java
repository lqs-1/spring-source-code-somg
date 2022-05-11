package spring.demo.test.config;

import org.springframework.context.annotation.*;
import spring.demo.test.Man;
import spring.demo.test.service.UserService;
import spring.demo.test.service.impl.UserServiceImpl;

/**
 * @author : 李奇凇
 * @date : 2022/5/10 22:09
 * @do :
 */


@Configuration
@ComponentScan("spring.demo.test")
@EnableAspectJAutoProxy
public class AnnotationApplicationContext {


	@Bean
	public UserService userService(){
		return new UserServiceImpl();
	}

	@Bean
	public Man man(){
		return new Man();
	}


}
