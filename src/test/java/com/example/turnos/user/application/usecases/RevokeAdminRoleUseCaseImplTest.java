package com.example.turnos.user.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevokeAdminRoleUseCaseImplTest {

	@Mock
	private UserRepository repository;

	@InjectMocks
	private RevokeAdminRoleUseCaseImpl useCase;

	@Test
	void removesTheAdminRoleAndKeepsTheUserRole() {
		User admin = User.builder().id(1L).login("admin").authorities(Set.of("ROLE_USER", "ROLE_ADMIN")).build();
		when(repository.findAllByAuthority("ROLE_ADMIN")).thenReturn(List.of(admin));

		useCase.revokeAdminRole();

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(repository).update(eq(1L), captor.capture());
		assertThat(captor.getValue().getAuthorities()).containsExactly("ROLE_USER");
	}

	@Test
	void doesNothingWhenThereIsNoAdmin() {
		when(repository.findAllByAuthority("ROLE_ADMIN")).thenReturn(List.of());

		useCase.revokeAdminRole();

		verify(repository, never()).update(any(), any());
	}

}
