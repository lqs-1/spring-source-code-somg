package spring.demo.test.beanTest;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import spring.demo.test.domain.User;
import spring.demo.test.service.UserService;

import java.util.List;

public class XmlBeanTest {
	public static void main(String[] args) {
		// xml配置文件方式
		ClassPathXmlApplicationContext caApp = new ClassPathXmlApplicationContext("XmlBeanApplication.xml");
		String[] beanDefinitionNames = caApp.getBeanDefinitionNames();


		for (String beanDefinitionName : beanDefinitionNames) {
			System.out.println(beanDefinitionName);
		}


	}
}
