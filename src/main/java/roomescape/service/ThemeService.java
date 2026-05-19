package roomescape.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.common.config.ClockProvider;
import roomescape.common.exception.RoomEscapeException;
import roomescape.common.exception.code.ThemeErrorCode;
import roomescape.dao.ReservationDao;
import roomescape.dao.ThemeDao;
import roomescape.domain.Theme;
import roomescape.dto.request.ThemeRequest;
import roomescape.dto.response.ThemeResponse;

@Service
@Transactional
public class ThemeService {
    private static final int POPULAR_THEME_PERIOD_DAYS = 6;

    private final ThemeDao themeDao;
    private final ReservationDao reservationDao;
    private final ClockProvider clockProvider;

    public ThemeService(ThemeDao themeDao, ReservationDao reservationDao, ClockProvider clockProvider) {
        this.themeDao = themeDao;
        this.reservationDao = reservationDao;
        this.clockProvider = clockProvider;
    }

    public ThemeResponse addTheme(ThemeRequest request) {
        validateUniqueTheme(request.name());
        Theme savedTheme = themeDao.insert(request.toTheme());
        return ThemeResponse.from(savedTheme);
    }

    @Transactional(readOnly = true)
    public List<ThemeResponse> getThemes() {
        return themeDao.selectAll().stream()
                .map(ThemeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ThemeResponse> getPopularThemes() {
        LocalDate endDate = LocalDate.now(clockProvider.getClock());
        LocalDate startDate = endDate.minusDays(POPULAR_THEME_PERIOD_DAYS);

        List<Theme> popularThemes = themeDao.selectPopularThemesByPeriod(startDate, endDate.minusDays(1));
        return popularThemes.stream()
                .map(ThemeResponse::from)
                .toList();
    }

    public void deleteTheme(long themeId) {
        Optional<Theme> theme = themeDao.selectById(themeId);
        if (theme.isEmpty()) {
            throw new RoomEscapeException(ThemeErrorCode.NOT_FOUND);
        }

        validateThemeIncludeReservation(themeId);
        themeDao.delete(themeId);
    }

    private void validateUniqueTheme(String name) {
        boolean exists = themeDao.existsByName(name);
        if (exists) {
            throw new RoomEscapeException(ThemeErrorCode.DUPLICATE);
        }
    }

    private void validateThemeIncludeReservation(long themeId) {
        boolean existsByThemeId = reservationDao.existsByThemeId(themeId);
        if (existsByThemeId) {
            throw new RoomEscapeException(ThemeErrorCode.THEME_CANNOT_DELETE);
        }
    }
}
