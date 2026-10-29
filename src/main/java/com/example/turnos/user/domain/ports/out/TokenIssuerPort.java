package com.example.turnos.user.domain.ports.out;

import com.example.turnos.user.domain.model.User;

/**
 * Emisión del token de sesión de un usuario. Es un puerto de salida porque el formato del token
 * (JWT), la firma y su vigencia son tecnología y configuración: el caso de uso solo sabe que,
 * si las credenciales son correctas, el usuario recibe un token.
 */
public interface TokenIssuerPort {

	String issue(User user, boolean rememberMe);

}
