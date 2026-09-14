package com.highpass.runspot.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.highpass.runspot.auth.domain.User;
import com.highpass.runspot.auth.domain.UserBlock;
import com.highpass.runspot.auth.domain.dao.UserBlockRepository;
import com.highpass.runspot.auth.domain.dao.UserRepository;
import com.highpass.runspot.auth.exception.UserErrorCode;
import com.highpass.runspot.auth.exception.UserException;
import com.highpass.runspot.auth.service.dto.response.BlockedUserResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @Mock UserRepository users;
    @Mock UserBlockRepository userBlocks;
    @InjectMocks UserBlockService service;

    @Test
    void 자기_자신은_차단할_수_없다() {
        assertThatThrownBy(() -> service.block(1L, 1L))
                .isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("exceptionType", UserErrorCode.SELF_BLOCK_NOT_ALLOWED);
    }

    @Test
    void 이미_차단한_사용자는_다시_차단할_수_없다() {
        when(userBlocks.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.block(1L, 2L))
                .isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("exceptionType", UserErrorCode.ALREADY_BLOCKED);
    }

    @Test
    void 정상적으로_차단한다() {
        User blocker = User.builder().id(1L).build();
        User blocked = User.builder().id(2L).build();
        when(userBlocks.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);
        when(users.findById(1L)).thenReturn(Optional.of(blocker));
        when(users.findById(2L)).thenReturn(Optional.of(blocked));

        service.block(1L, 2L);

        org.mockito.Mockito.verify(userBlocks).save(org.mockito.ArgumentMatchers.any(UserBlock.class));
    }

    @Test
    void 차단_내역이_없으면_해제할_수_없다() {
        when(userBlocks.findByBlockerIdAndBlockedId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unblock(1L, 2L))
                .isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("exceptionType", UserErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    void 차단_목록을_조회한다() {
        User blocker = User.builder().id(1L).build();
        User blocked = User.builder().id(2L).name("차단된유저").build();
        UserBlock userBlock = UserBlock.create(blocker, blocked);
        when(userBlocks.findByBlockerId(1L)).thenReturn(List.of(userBlock));

        List<BlockedUserResponse> result = service.getBlockedUsers(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(2L);
        assertThat(result.get(0).name()).isEqualTo("차단된유저");
    }
}
