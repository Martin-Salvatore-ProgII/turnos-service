package com.example.turnos.shared.infrastructure.web.advice;

import java.util.List;

import com.example.turnos.availability.application.exception.AvailabilityException;
import com.example.turnos.availability.application.exception.IntegrationUnavailableException;
import com.example.turnos.catalog.application.exception.CatalogTokenRejectedException;
import com.example.turnos.catalog.application.exception.CatalogUnavailableException;
import com.example.turnos.user.application.exception.UserException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
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
	private static final String CATALOG_UNAVAILABLE = "CATALOG_UNAVAILABLE";
	private static final String INTEGRATION_UNAVAILABLE = "INTEGRATION_UNAVAILABLE";

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

	// Sin los datos vigentes del catálogo no se inicia la operación (CU-03 3c, ENUNCIADO §8).
	@ExceptionHandler(CatalogUnavailableException.class)
	public ResponseEntity<Object> handleCatalogUnavailable(CatalogUnavailableException ex, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
		problem.setProperty("code", CATALOG_UNAVAILABLE);
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, request);
	}

	// Un profesional inexistente es 404, como en la cátedra y el catálogo (REF §13.1). La fecha fuera de
	// rango y el profesional deshabilitado son errores del pedido: 400 con su código (contratos.md).
	@ExceptionHandler(AvailabilityException.class)
	public ResponseEntity<Object> handleAvailabilityException(AvailabilityException ex, WebRequest request) {
		HttpStatus status = ex.getCode() == AvailabilityException.Code.PROFESSIONAL_NOT_FOUND
				? HttpStatus.NOT_FOUND
				: HttpStatus.BAD_REQUEST;
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		problem.setProperty("code", ex.getCode().name());
		return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
	}

	// Sin las ocupaciones de la cátedra no se muestran slots (CU-03 4a).
	@ExceptionHandler(IntegrationUnavailableException.class)
	public ResponseEntity<Object> handleIntegrationUnavailable(IntegrationUnavailableException ex, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
		problem.setProperty("code", INTEGRATION_UNAVAILABLE);
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, request);
	}

	// El token venció entre la llamada de la app y la del catálogo (ADR-0034): se responde como
	// cualquier token vencido, 401 sin code, y el usuario vuelve a iniciar sesión.
	@ExceptionHandler(CatalogTokenRejectedException.class)
	public ResponseEntity<Object> handleCatalogTokenRejected(CatalogTokenRejectedException ex, WebRequest request) {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
		return handleExceptionInternal(ex, problem, headers, HttpStatus.UNAUTHORIZED, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
		// El detalle real queda en el log; al cliente no se le muestran datos internos.
		log.error("Unexpected error", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
		return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
	}

	// Un parámetro con un tipo que no corresponde ("abc" donde va un número): se informa qué campo es,
	// como en cualquier error de validación.
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String field = ex.getPropertyName();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Invalid value for " + field);
		problem.setProperty("fieldErrors", List.of(FieldErrorResponse.builder()
				.field(field)
				.message("has an invalid format")
				.build()));
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	// Todos los errores pasan por acá: es el lugar para agregar el path a cada respuesta.
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			if (request instanceof ServletWebRequest servletRequest) {
				problem.setProperty("path", servletRequest.getRequest().getRequestURI());
			}
			// Un 400 sin código propio lo arma Spring (JSON mal formado, tipo inválido, parámetro que
			// falta): para el contrato es un VALIDATION_ERROR, y así ningún 400 llega sin code.
			boolean withoutCode = problem.getProperties() == null || !problem.getProperties().containsKey("code");
			if (statusCode.value() == HttpStatus.BAD_REQUEST.value() && withoutCode) {
				problem.setProperty("code", VALIDATION_ERROR);
			}
		}
		return response;
	}

}
