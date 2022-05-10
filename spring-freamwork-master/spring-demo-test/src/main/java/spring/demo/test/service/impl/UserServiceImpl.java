package spring.demo.test.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import spring.demo.test.service.UserService;

/**
 * @author : 李奇凇
 * @date : 2022/5/8 13:19
 * @do :
 */

@Service
public class UserServiceImpl implements UserService {
	@Override
	public String ToString() {
		return "获取到了";
	}
}
