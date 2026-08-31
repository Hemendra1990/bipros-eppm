package com.bipros.activity.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baseline schedule variance used to compare baseline dates against the activity's PLANNED
 * dates, so it read 0 variance even after real slippage (a fresh baseline snapshots planned ==
 * baseline). {@link Activity#currentStartDate()} / {@link Activity#currentFinishDate()} are the
 * single source of truth for what "current" means: actual (if the activity has really
 * started/finished) takes priority, then the scheduler's forecast (early*), then planned as the
 * last resort for activities that haven't been touched by progress or the scheduler yet.
 */
@DisplayName("Activity.currentStartDate() / currentFinishDate()")
class ActivityCurrentDatesTest {

  private final LocalDate planned = LocalDate.of(2025, 1, 1);
  private final LocalDate early = LocalDate.of(2025, 1, 5);
  private final LocalDate actual = LocalDate.of(2025, 1, 10);

  @Test
  @DisplayName("complete activity: actual finish present -> current finish is actual finish")
  void actualFinishPresent_returnsActual() {
    Activity a = new Activity();
    a.setPlannedFinishDate(planned);
    a.setEarlyFinishDate(early);
    a.setActualFinishDate(actual);

    assertThat(a.currentFinishDate()).isEqualTo(actual);
  }

  @Test
  @DisplayName("in-progress activity: no actual finish, early finish present -> current finish is early finish")
  void noActual_earlyFinishPresent_returnsEarly() {
    Activity a = new Activity();
    a.setPlannedFinishDate(planned);
    a.setEarlyFinishDate(early);

    assertThat(a.currentFinishDate()).isEqualTo(early);
  }

  @Test
  @DisplayName("neither actual nor early finish -> current finish falls back to planned finish")
  void noActualNoEarly_fallsBackToPlanned() {
    Activity a = new Activity();
    a.setPlannedFinishDate(planned);

    assertThat(a.currentFinishDate()).isEqualTo(planned);
  }

  @Test
  @DisplayName("started activity: actual start present -> current start is actual start")
  void actualStartPresent_returnsActual() {
    Activity a = new Activity();
    a.setPlannedStartDate(planned);
    a.setEarlyStartDate(early);
    a.setActualStartDate(actual);

    assertThat(a.currentStartDate()).isEqualTo(actual);
  }

  @Test
  @DisplayName("not-yet-started activity: no actual start, early start present -> current start is early start")
  void noActual_earlyStartPresent_returnsEarly() {
    Activity a = new Activity();
    a.setPlannedStartDate(planned);
    a.setEarlyStartDate(early);

    assertThat(a.currentStartDate()).isEqualTo(early);
  }

  @Test
  @DisplayName("neither actual nor early start -> current start falls back to planned start")
  void noActualNoEarly_startFallsBackToPlanned() {
    Activity a = new Activity();
    a.setPlannedStartDate(planned);

    assertThat(a.currentStartDate()).isEqualTo(planned);
  }
}
