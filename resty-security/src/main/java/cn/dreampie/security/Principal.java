package cn.dreampie.security;


import cn.dreampie.common.entity.Entity;

import java.io.Serializable;
import java.util.Set;

/**
 * Date: 1/30/13
 * Time: 6:30 PM
 */
public class Principal<M extends Entity> implements Serializable {
  public static final String PRINCIPAL_DEF_KEY = "_principal";
  private String username;
  private String passwordHash;
  private String salt;
  private Set<String> credentials;
  private M model;

  public Principal(String username, String passwordHash, Set<String> credentials, M model) {
    this(username, passwordHash, null, credentials, model);
  }

  public Principal(String username, String passwordHash, String salt, Set<String> credentials, M model) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.salt = salt;
    this.credentials = credentials;
    this.model = model;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getSalt() {
    return salt;
  }

  public Set<String> getCredentials() {
    return credentials;
  }

  public boolean hasCredential(String permission) {
    return credentials.contains(permission);
  }

  public M getModel() {
    return model;
  }
}
