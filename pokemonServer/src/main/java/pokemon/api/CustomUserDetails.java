package pokemon.api;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CustomUserDetails implements UserDetails {

    private final int id;
    private final String username;
    private final String password;

    public CustomUserDetails(Map<String, Object> player) {
        this.id = (int) player.get("id");
        this.username = (String) player.get("username");
        this.password = (String) player.get("password");
    }

    public int getId() { return id; }

    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
