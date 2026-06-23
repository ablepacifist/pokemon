package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;

import java.util.Map;

@Service
public class PlayerDetailsService implements UserDetailsService {

    @Autowired
    private PokemonDatabase db;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            Map<String, Object> player = db.getPlayerByUsername(username);
            if (player == null) throw new UsernameNotFoundException("User not found: " + username);
            return new CustomUserDetails(player);
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UsernameNotFoundException("DB error loading user: " + e.getMessage());
        }
    }
}
