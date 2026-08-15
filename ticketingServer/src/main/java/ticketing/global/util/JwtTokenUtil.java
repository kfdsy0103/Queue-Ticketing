package ticketing.global.util;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import ticketing.global.apiPayload.code.GeneralErrorCode;
import ticketing.global.apiPayload.exception.GeneralException;

@Slf4j
@Component
public class JwtTokenUtil {

	private final SecretKey secretKey;
	private final long defaultExpirationMs;

	public JwtTokenUtil(
		@Value("${spring.jwt.secret}") String secret,
		@Value("${spring.jwt.expiration}") long defaultExpirationMs
	) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.defaultExpirationMs = defaultExpirationMs;
	}

	/**
	 * 주어진 claim들로 JWT를 발급합니다. (기본 만료 시간 적용)
	 */
	public String generateToken(Map<String, Object> claims) {
		return generateToken(claims, defaultExpirationMs);
	}

	/**
	 * 주어진 claim들과 만료 시간(ms)으로 JWT를 발급합니다.
	 */
	public String generateToken(Map<String, Object> claims, long expirationMs) {
		Date now = new Date();
		Date expiration = new Date(now.getTime() + expirationMs);

		return Jwts.builder()
			.claims(claims)
			.issuedAt(now)
			.expiration(expiration)
			.signWith(secretKey)
			.compact();
	}

	/**
	 * 토큰에서 특정 claim 값을 추출합니다.
	 */
	public <T> T getClaim(String token, String key, Class<T> type) {
		return parseClaims(token).get(key, type);
	}

	/**
	 * 토큰의 유효성을 검증하고 Claims를 반환합니다.
	 */
	public Claims parseClaims(String token) {
		try {
			return Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
		}
		catch (SecurityException | MalformedJwtException e) {
			log.error("잘못된 JWT 서명입니다.", e);
			throw new GeneralException(GeneralErrorCode.INVALID_TOKEN);
		}
		catch (ExpiredJwtException e) {
			log.warn("만료된 JWT 토큰입니다.", e);
			throw new GeneralException(GeneralErrorCode.TOKEN_EXPIRED);
		}
		catch (UnsupportedJwtException e) {
			log.error("지원되지 않는 JWT 토큰입니다.", e);
			throw new GeneralException(GeneralErrorCode.INVALID_TOKEN);
		}
		catch (IllegalArgumentException e) {
			log.error("JWT 토큰이 잘못되었습니다.", e);
			throw new GeneralException(GeneralErrorCode.INVALID_TOKEN);
		}
	}
}
