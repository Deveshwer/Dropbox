package com.example.dropbox.metadata.auth;

import com.example.dropbox.metadata.users.User;
import com.example.dropbox.metadata.users.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            jwt = authHeader.substring(7);
            userEmail = jwtService.extractEmail(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findByEmail(userEmail)
                        .orElse(null);

                if (user != null && jwtService.isTokenValid(jwt, user)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    Collections.emptyList()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            filterChain.doFilter(request, response);
        } catch(Exception e) {
        filterChain.doFilter(request, response);
        return;
        }
    } 
}

/* Explanation:

 # What each part means

  ## extends OncePerRequestFilter

  Ensures this filter runs once per request.

  That is what you want for JWT auth.

  ———

  ## Read Authorization header

  final String authHeader = request.getHeader("Authorization");

  This reads something like:

  Authorization: Bearer eyJhbGciOi...

  ———

  ## Skip if header missing

  if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
  }

  Meaning:

  - if client is not sending a bearer token
  - do not fail here
  - just continue request processing

  This is important because some endpoints are public.

  ———

  ## Extract raw JWT

  jwt = authHeader.substring(7);

  Why 7?
  Because "Bearer " is 7 characters.

  So if header is:

  Bearer abc.def.ghi

  this extracts just:

  abc.def.ghi

  ———

  ## Extract email from token

  userEmail = jwtService.extractEmail(jwt);

  This parses the token and reads sub.

  ———

  ## Avoid overriding existing authentication

  SecurityContextHolder.getContext().getAuthentication() == null

  If Spring already has an authenticated user in context, do not replace it.

  ———

  ## Load user from DB

  User user = userRepository.findByEmail(userEmail).orElse(null);

  This turns token identity into a real application user record.

  ———

  ## Validate token

  if (user != null && jwtService.isTokenValid(jwt, user))

  This ensures:

  - token subject matches user
  - token not expired
  - token signature valid

  ———

  ## Create authentication object

  UsernamePasswordAuthenticationToken authToken =
          new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());

  This tells Spring Security:

  - this request is authenticated
  - principal is this user
  - no authorities/roles yet

  ———

  ## Put it in security context

  SecurityContextHolder.getContext().setAuthentication(authToken);

  This is the key step.

  After this, downstream code can treat the request as authenticated.

*/