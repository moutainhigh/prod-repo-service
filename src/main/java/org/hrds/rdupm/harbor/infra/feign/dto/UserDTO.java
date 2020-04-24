package org.hrds.rdupm.harbor.infra.feign.dto;

import java.util.Date;

/**
 * description
 *
 * @author chenxiuhong 2020/03/17 2:29 下午
 */
public class UserDTO {
	private Long id;

	private String loginName;

	private String email;

	private Long organizationId;

	private String password;

	private String realName;

	private String phone;

	private String imageUrl;

	private String profilePhoto;

	private Boolean isEnabled;

	private Boolean ldap;

	private String language;

	private String timeZone;

	private Date lastPasswordUpdatedAt;

	private Date lastLoginAt;

	private Boolean isLocked;

	private Date lockedUntilAt;

	private Integer passwordAttempt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getLoginName() {
		return loginName;
	}

	public void setLoginName(String loginName) {
		this.loginName = loginName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Long getOrganizationId() {
		return organizationId;
	}

	public void setOrganizationId(Long organizationId) {
		this.organizationId = organizationId;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getRealName() {
		return realName;
	}

	public void setRealName(String realName) {
		this.realName = realName;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public String getProfilePhoto() {
		return profilePhoto;
	}

	public void setProfilePhoto(String profilePhoto) {
		this.profilePhoto = profilePhoto;
	}

	public Boolean getEnabled() {
		return isEnabled;
	}

	public void setEnabled(Boolean enabled) {
		isEnabled = enabled;
	}

	public Boolean getLdap() {
		return ldap;
	}

	public void setLdap(Boolean ldap) {
		this.ldap = ldap;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public Date getLastPasswordUpdatedAt() {
		return lastPasswordUpdatedAt;
	}

	public void setLastPasswordUpdatedAt(Date lastPasswordUpdatedAt) {
		this.lastPasswordUpdatedAt = lastPasswordUpdatedAt;
	}

	public Date getLastLoginAt() {
		return lastLoginAt;
	}

	public void setLastLoginAt(Date lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}

	public Boolean getLocked() {
		return isLocked;
	}

	public void setLocked(Boolean locked) {
		isLocked = locked;
	}

	public Date getLockedUntilAt() {
		return lockedUntilAt;
	}

	public void setLockedUntilAt(Date lockedUntilAt) {
		this.lockedUntilAt = lockedUntilAt;
	}

	public Integer getPasswordAttempt() {
		return passwordAttempt;
	}

	public void setPasswordAttempt(Integer passwordAttempt) {
		this.passwordAttempt = passwordAttempt;
	}

	@Override
	public String toString() {
		return "UserDTO{" +
				"id=" + id +
				", loginName='" + loginName + '\'' +
				", email='" + email + '\'' +
				", organizationId=" + organizationId +
				", password='" + password + '\'' +
				", realName='" + realName + '\'' +
				", phone='" + phone + '\'' +
				", imageUrl='" + imageUrl + '\'' +
				", profilePhoto='" + profilePhoto + '\'' +
				", isEnabled=" + isEnabled +
				", ldap=" + ldap +
				", language='" + language + '\'' +
				", timeZone='" + timeZone + '\'' +
				", lastPasswordUpdatedAt=" + lastPasswordUpdatedAt +
				", lastLoginAt=" + lastLoginAt +
				", isLocked=" + isLocked +
				", lockedUntilAt=" + lockedUntilAt +
				", passwordAttempt=" + passwordAttempt +
				'}';
	}
}
