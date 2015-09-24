package cn.dreampie.security;

import static cn.dreampie.common.util.Checker.checkNotNull;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import cn.dreampie.common.http.exception.WebException;
import cn.dreampie.common.http.result.HttpStatus;
import cn.dreampie.common.util.matcher.AntPathMatcher;
import cn.dreampie.log.Logger;
import cn.dreampie.security.credential.Credential;
import cn.dreampie.security.credential.Credentials;
import cn.dreampie.security.encode.RandomString;
import cn.dreampie.security.sign.Token;

/**
 * Created by wangrenhui on 14/12/23.
 */
public class Subject {
	private static final Logger logger = Logger.getLogger(Subject.class);

	private static final ThreadLocal<Session> sessionTL = new ThreadLocal<Session>();
	private static Credentials credentials;
	private static PasswordService passwordService;
	private static int rememberDay;

	static void init(int rememberDay, Credentials credentials,
			PasswordService passwordService) {
		Subject.rememberDay = rememberDay;
		Subject.credentials = credentials;
		Subject.passwordService = passwordService;
	}

	static Session current() {
		return sessionTL.get();
	}

	static Session updateCurrent(Session session) {
		if (session != current()) {
			sessionTL.set(session);
		}
		return session;
	}

	private static void removeCurrent() {
		sessionTL.remove();
	}

	private static Session authenticateAs(Principal principal, long expires) {
		String sessionKey = UUID.randomUUID().toString();
		return updateCurrent(new Session(sessionKey, principal, current()
				.getValues(), expires));
	}

	private static Session clearPrincipal() {
		Session session = current();
		Principal principal = session.getPrincipal();
		if (principal != null) {
			credentials.removePrincipal(principal.getUsername());
		}
		return updateCurrent(new Session());
	}

	/**
	 * login user
	 *
	 * @param username
	 * @param password
	 * @param rememberMe
	 * @return
	 */
	public static void login(String username, String password,
			boolean rememberMe) {
		checkNotNull(username, "Username could not be null.");
		checkNotNull(password, "Password could not be null.");
		Principal principal = credentials.getPrincipal(username);
		if (principal == null) {
			throw new WebException(HttpStatus.NOT_FOUND, "User not found.");
		}
		boolean match = false;
		String salt = principal.getSalt();
		if (salt != null && !salt.isEmpty()) {
			match = passwordService.match(password,
					principal.getPasswordHash(), salt);
		} else {
			match = passwordService
					.match(password, principal.getPasswordHash());
		}

		if (match) {
			// 授权用户
			// 时间
			long expires = -1;
			if (rememberMe) {
				Calendar cal = Calendar.getInstance();
				cal.add(Calendar.DATE, rememberDay);
				expires = cal.getTimeInMillis();
			}
			// 授权用户
			authenticateAs(principal, expires);
			logger.info("Session authentication as " + username);
		} else {
			throw new WebException(HttpStatus.UNPROCESSABLE_ENTITY,
					"Password not match.");
		}
	}

	public static void logout() {
		// add cache
		Principal principal = current().getPrincipal();
		if (principal != null) {
			logger.info("Session leave authentication "
					+ principal.getUsername());
		}
		// 清理用户
		clearPrincipal();
	}

	public static void login(String username, String password) {
		login(username, password, false);
	}

	public static long getExpires() {
		return current().getExpires();
	}

	public static Principal getPrincipal() {
		return current().getPrincipal();
	}

	public static Map<String, String> getValues() {
		return current().getValues();
	}

	public static String get(String key) {
		return current().get(key);
	}

	public static void set(String key, String value) {
		current().set(key, value);
	}

	public static String remove(String key) {
		return current().remove(key);
	}

	/**
	 * 当前api需要的权限值
	 *
	 * @param httpMethod
	 *            httpMethod
	 * @param path
	 *            path
	 * @return value
	 */
	public static String need(String httpMethod, String path) {
		Map<String, Map<String, Set<Credential>>> credentialMap = credentials
				.getAllCredentials();

		String value;
		if (credentialMap.containsKey(httpMethod)) {
			// 匹配method的map
			value = matchPath(httpMethod, path, credentialMap);
			if (value == null) {
				value = matchPath("*", path, credentialMap);
			}
		} else {
			value = matchPath("*", path, credentialMap);
		}
		return value;
	}

	/**
	 * 匹配规则，优先httpMethod，其次相同的起始位置
	 *
	 * @param httpMethod
	 *            httpMethod
	 * @param path
	 *            path
	 * @param credentialMap
	 *            credentialMap
	 * @return value
	 */
	private static String matchPath(String httpMethod, String path,
			Map<String, Map<String, Set<Credential>>> credentialMap) {
		if (credentialMap != null && credentialMap.size() > 0) {
			Map<String, Set<Credential>> credentials = credentialMap
					.get(httpMethod);
			if (credentials != null && credentials.size() > 0) {
				Set<Map.Entry<String, Set<Credential>>> credentialsEntry = credentials
						.entrySet();
				Set<Credential> credentialSet;
				for (Map.Entry<String, Set<Credential>> credentialEntry : credentialsEntry) {
					if (path.startsWith(credentialEntry.getKey())) {
						credentialSet = credentialEntry.getValue();
						for (Credential credential : credentialSet) {
							if (AntPathMatcher.instance().match(
									credential.getAntPath(), path)) {
								return credential.getValue();
							}
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * 检测权限
	 *
	 * @param httpMethod
	 *            httpMethod
	 * @param path
	 *            path
	 */
	public static void check(String httpMethod, String path) {
		// TODO 实现黑名单，白名单的功能
		String needCredential = need(httpMethod, path);
		logger.info(httpMethod + " " + path + " need credential "
				+ needCredential);
		if (needCredential != null) {
			Principal principal = current().getPrincipal();
			if (principal != null) {
				if (!principal.hasCredential(needCredential)) {
					throw new WebException(HttpStatus.FORBIDDEN);
				}
			} else {
				throw new WebException(HttpStatus.UNAUTHORIZED);
			}
		}
	}

	/**
	 * 判断是否有当前api权限
	 *
	 * @param httpMethod
	 *            httpMethod
	 * @param path
	 *            path
	 * @return boolean
	 */
	public static boolean has(String httpMethod, String path) {
		String needCredential = need(httpMethod, path);
		if (needCredential != null) {
			Principal principal = current().getPrincipal();
			if (principal != null) {
				if (principal.hasCredential(needCredential)) {
					return true;
				}
			}
		} else {
			return true;
		}
		return false;
	}

	/**
	 * 生成客户端认证的apikey和secretkey
	 * 每次
	 * @return
	 */
//	public static Map<String, String> generateClientKey() {
//		// TODO cqiyi:随机生成apikey和secretkey
//		// map.put(key, value);
//		Token token = Token.create();
//		Map<String, String> map = new HashMap<String, String>();
//		map.put(key, value)
//	}


	void set(Map<String, String> values) {
		current().set(values);
	}

}
