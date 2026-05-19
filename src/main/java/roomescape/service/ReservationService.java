package roomescape.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.common.config.ClockProvider;
import roomescape.common.exception.RoomEscapeException;
import roomescape.common.exception.code.ReservationErrorCode;
import roomescape.common.exception.code.ReservationTimeErrorCode;
import roomescape.common.exception.code.ThemeErrorCode;
import roomescape.dao.ReservationDao;
import roomescape.dao.ReservationTimeDao;
import roomescape.dao.ThemeDao;
import roomescape.domain.Reservation;
import roomescape.domain.ReservationTime;
import roomescape.domain.Theme;
import roomescape.dto.request.ReservationRequest;
import roomescape.dto.request.UpdateReservationRequest;
import roomescape.dto.response.ReservationResponse;

@Service
@Transactional
public class ReservationService {
    private final ReservationDao reservationDao;
    private final ReservationTimeDao reservationTimeDao;
    private final ThemeDao themeDao;
    private final ClockProvider clockProvider;

    public ReservationService(ReservationDao reservationDao, ReservationTimeDao reservationTimeDao, ThemeDao themeDao,
                              ClockProvider clockProvider) {
        this.reservationDao = reservationDao;
        this.reservationTimeDao = reservationTimeDao;
        this.themeDao = themeDao;
        this.clockProvider = clockProvider;
    }

    public ReservationResponse addReservation(ReservationRequest request) {
        ReservationTime reservationTime = getTime(request.timeId());
        Theme theme = getTheme(request.themeId());

        validateUniqueReservation(request.date(), request.timeId(), request.themeId());
        validatePastDatetime(request.date(), reservationTime);

        Reservation reservation = request.toReservation(reservationTime, theme);
        Reservation savedReservation = reservationDao.insert(reservation);
        return ReservationResponse.from(savedReservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations() {
        List<Reservation> reservations = reservationDao.select();
        return reservations.stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservation(String name) {
        List<Reservation> reservations = reservationDao.selectByName(name);
        return reservations.stream()
                .map(ReservationResponse::from)
                .toList();
    }

    public ReservationResponse update(Long reservationId, UpdateReservationRequest request) {
        Reservation reservation = getReservation(reservationId);
        ReservationTime time = getTime(request.timeId());
        validateUniqueExcludingSelf(request.date(), request.timeId(), reservation.getTheme().getId(),
                reservation.getId());
        validatePastDatetime(request.date(), time);

        Reservation updateReservation = reservationDao.update(reservationId, request.date(), request.timeId());
        return ReservationResponse.from(updateReservation);
    }

    public void delete(Long reservationId) {
        int deleted = reservationDao.delete(reservationId);
        if (deleted == 0) {
            throw new RoomEscapeException(ReservationErrorCode.NOT_FOUND);
        }
    }

    private ReservationTime getTime(long timeId) {
        return reservationTimeDao.selectById(timeId)
                .orElseThrow(() -> new RoomEscapeException(ReservationTimeErrorCode.NOT_FOUND));
    }

    private Theme getTheme(long themeId) {
        return themeDao.selectById(themeId)
                .orElseThrow(() -> new RoomEscapeException(ThemeErrorCode.NOT_FOUND));
    }

    private void validateUniqueReservation(LocalDate date, long timeId, long themeId) {
        boolean exists = reservationDao.existsByDateAndTimeIdAndThemeId(date, timeId, themeId);
        if (exists) {
            throw new RoomEscapeException(ReservationErrorCode.DUPLICATE);
        }
    }

    private void validateUniqueExcludingSelf(LocalDate date, long timeId, long themeId, long id) {
        boolean exists = reservationDao.existsDuplicateExcluding(date, timeId, themeId, id);
        if (exists) {
            throw new RoomEscapeException(ReservationErrorCode.DUPLICATE);
        }
    }

    private void validatePastDatetime(LocalDate date, ReservationTime reservationTime) {
        LocalDateTime now = LocalDateTime.now(clockProvider.getClock());
        LocalDateTime reservationDateAndTime = LocalDateTime.of(date, reservationTime.getStartAt());

        if (reservationDateAndTime.isBefore(now)) {
            throw new RoomEscapeException(ReservationErrorCode.PAST_DATETIME);
        }
    }

    private Reservation getReservation(Long reservationId) {
        return reservationDao.selectById(reservationId)
                .orElseThrow(() -> new RoomEscapeException(ReservationErrorCode.NOT_FOUND));
    }
}
