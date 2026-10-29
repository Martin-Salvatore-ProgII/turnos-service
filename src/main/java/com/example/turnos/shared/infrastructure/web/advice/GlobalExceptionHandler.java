package com.example.turnos.shared.infrastructure.web.advice;

import java.util.List;

import com.example.turnos.user.application.exception.UserException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce los errores a {@code application/problem+json} con la forma del esquema {@code Problem}
 * del contrato. Los errores propios de Spring MVC (ruta inexistente, método no permitido, JSON mal
 * formado) los resuelve la clase base.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldErrorResponse> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> FieldErrorResponse.builder()
						.objectName(error.getObjectName())
						.field(error.getField())
						.message(error.getDefaultMessage())
						.build())
				.toList();
		ProblemDetail problem = ex.getBody();
		problem.setProperty("code", VALIDATION_ERROR);
		problem.setProperty("fieldErrors", fieldErrors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	// Un login o un email repetidos son errores del pedido: 400 con el código funcional del contrato.
	// Las credenciales incorrectas son un 401 sin código, como define el contrato para ese estado.
	@ExceptionHandler(UserException.class)
	public ResponseEntity<Object> handleUserException(UserException ex, WebRequest request) {
		boolean invalidCredentials = ex.getCode() == UserException.Code.INVALID_CREDENTIALS;
		HttpStatus status = invalidCredentials ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		if (!invalidCredentials) {
			problem.setProperty("code", ex.getCode().name());
		}
		return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		// El detalle real queda en el log; al cliente no se le muestran datos internos.
		log.error("Unexpected error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	// Todos los errores pasan por acá: es el lugar para agregar el path a cada respuesta.
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem
				&& request instanceof ServletWebRequest servletRequest) {
			problem.setProperty("path", servletRequest.getRequest().getRequestURI());
		}
		return response;
	}

}
