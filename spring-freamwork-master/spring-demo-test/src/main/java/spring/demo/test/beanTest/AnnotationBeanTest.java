package spring.demo.test.beanTest;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import spring.demo.test.config.AnnotationApplicationContext;
import spring.demo.test.service.UserService;

/**
 * @author : 李奇凇
 * @date : 2022/5/10 21:58
 * @do :
 */
public class AnnotationBeanTest {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext anApp = new AnnotationConfigApplicationContext(AnnotationApplicationContext.class);


		AnnotationApplicationContext bean = anApp.getBean(AnnotationApplicationContext.class);


		System.out.println(bean);

//		UserService bean = annotationConfigApplicationContext.getBean(UserService.class);


//		System.out.println(bean.ToString());




	}
}
