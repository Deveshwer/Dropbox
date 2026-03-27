package com.example.dropbox.metadata.auth;

import com.example.dropbox.metadata.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token, User user) {
        String email = extractEmail(token);
        return email.equals(user.getEmail()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

/* Explanation:


  # First: what is a JWT structurally?

  A JWT is usually 3 Base64URL-encoded parts separated by dots:

  header.payload.signature

  Example shape:

  aaaaa.bbbbb.ccccc

  ### 1. Header

  Contains metadata such as:

  {
    "alg": "HS256",
    "typ": "JWT"
  }

  Meaning:

  - algorithm used = HS256
  - token type = JWT

  ### 2. Payload

  Contains claims:

  {
    "sub": "test@example.com",
    "userId": "11111111-1111-1111-1111-111111111111",
    "iat": 1711100000,
    "exp": 1711103600
  }

  ### 3. Signature

  This is the cryptographic signature created using:

  - header
  - payload
  - your secret key

  This is what prevents tampering.

  If someone changes payload data, the signature no longer matches.

  ———

 isTokenValid answers one question:

  “Can I trust this JWT as belonging to this user right now?”

  Your method is:

  public boolean isTokenValid(String token, User user) {
      String email = extractEmail(token);
      return email.equals(user.getEmail()) && !isTokenExpired(token);
  }

  Let’s unpack it slowly.

  ———

  # 1. What is inside the JWT?

  When you create the token in generateToken, you put in:

  - subject = user email
  - userId as an extra claim
  - issuedAt
  - expiration

  Example conceptual token payload:

  {
    "sub": "test@example.com",
    "userId": "11111111-1111-1111-1111-111111111111",
    "iat": 1711100000,
    "exp": 1711103600
  }

  This payload is then signed using your secret key.

  That signature is important because it prevents tampering.

  ———

  # 2. What happens when someone sends a token back?

  Suppose a client calls:

  Authorization: Bearer <jwt>

  Your backend receives the token.

  Now the backend must decide:

  - was this token really issued by me?
  - was it modified?
  - who does it represent?
  - is it expired?

  That is what validation is about.

  ———

  # 3. What does extractEmail(token) do?

  This method:

  public String extractEmail(String token) {
      return extractClaim(token, Claims::getSubject);
  }

  means:

  - parse the token
  - verify the token signature using your secret
  - read the subject claim
  - return it

  If the token was tampered with, parsing itself will fail.

  So when extractEmail(token) succeeds, that already means:

  - token structure is valid
  - signature is valid
  - claims could be read

  In your current design, subject is the email.

  So:

  String email = extractEmail(token);

  might give:

  "test@example.com"

  ———

  # 4. Why compare it with user.getEmail()?

  This part:

  email.equals(user.getEmail())

  means:

  “Does the token actually belong to the user I think it belongs to?”

  Imagine this scenario:

  - token subject says alice@example.com
  - but the User object passed in is for bob@example.com

  Then the token should not be considered valid for Bob.

  So this comparison ensures identity consistency.

  In other words:

  - token says who it is for
  - backend compares that against the user record it loaded
  - if they match, identity check passes

  ———

   # 7. What is not explicitly visible here, but still important?

  This method does not manually check signature.
  But signature validation already happens indirectly inside:

  extractAllClaims(token)

  because this code:

  Jwts.parser()
      .verifyWith(getSigningKey())
      .build()
      .parseSignedClaims(token)

  does signature verification while parsing.

  So by the time extractEmail(token) or extractClaim(token, ...) returns, the JWT has already been cryptographically validated.

  That means isTokenValid is really checking:

  1. signature is valid
  2. subject matches expected user
  3. token is not expired


*/
