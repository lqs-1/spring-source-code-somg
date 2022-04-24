package com.springdemo.test;

import com.springdemo.configurattion.MyConfiguration;
import com.springdemo.pojo.User;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MyTest {
	public static void main(String[] args) {
		ApplicationContext app = new ClassPathXmlApplicationContext("application.xml");
		User user = app.getBean("user", User.class);
		System.out.println(user.getUserName());
	}
}
