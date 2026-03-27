package com.example.dropbox.metadata.auth;

  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.security.config.Customizer;
  import org.springframework.security.config.annotation.web.builders.HttpSecurity;
  import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
  import org.springframework.security.config.http.SessionCreationPolicy;
  import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
  import org.springframework.security.crypto.password.PasswordEncoder;
  import org.springframework.security.web.SecurityFilterChain;
  import lombok.RequiredArgsConstructor;
  import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

  @Configuration
  @EnableWebSecurity
  @RequiredArgsConstructor
  public class SecurityConfig {
      
      private final JwtAuthenticationFilter jwtAuthenticationFilter;

      @Bean
      public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
          http
                  .csrf(csrf -> csrf.disable())
                  .sessionManagement(session ->
                          session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                  )
                  .authorizeHttpRequests(auth -> auth
                          .requestMatchers("/api/auth/**").permitAll()
                          .anyRequest().authenticated()
                  )
                  .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

          return http.build();
      }

      @Bean
      public PasswordEncoder passwordEncoder() {
          return new BCryptPasswordEncoder();
      }
  }

  /* Explanation for passwordEncoder:

  ## Compile-time

  This line is enough for compilation:

  import org.springframework.security.crypto.password.PasswordEncoder;

  and this field is also fine for compilation:

  private final PasswordEncoder passwordEncoder;

  Why?
  Because the class/interface exists in the Spring Security dependency you already added.

  So from the compiler’s point of view, that is enough.

  ## Runtime

  But at runtime, Spring must create an actual object for this field.

  Since you are using constructor injection through @RequiredArgsConstructor, Spring will try to construct AuthService like this conceptually:

  new AuthService(userRepository, passwordEncoder, jwtService)

  Spring knows how to provide:

  - UserRepository
  - JwtService once you mark it as @Service

  But for PasswordEncoder, Spring does not automatically know which implementation to create unless you define one as a bean.

  For example:

  @Bean
  public PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder();
  }

  That usually goes in SecurityConfig.

  ## So the distinction is

  ### For compile:

  You only need the class/interface to exist in dependencies.

  ### For app startup:

  You need Spring to know how to instantiate and inject it.

  ———

  ## What would happen if you don’t define the bean

  Compilation will succeed.

  But when the app starts, Spring will fail with something like:

  No qualifying bean of type 'org.springframework.security.crypto.password.PasswordEncoder' available

  That is a dependency injection/runtime error, not a Java compilation error.

  ———

  ## Same logic for JwtService

  If JwtService is just referenced as a type and the class exists, compilation is fine.

  But if it is not annotated with @Service or otherwise registered as a bean, startup will fail similarly.

  ———

  ## Short answer

  - PasswordEncoder class is available from Spring Security, so compile is fine
  - but Spring still needs a bean instance at runtime
  - that bean is usually created in SecurityConfig


*/