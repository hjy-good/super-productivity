import { inject, Injectable } from '@angular/core';
import { createEffect } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { debounceTime, distinctUntilChanged, filter, tap } from 'rxjs/operators';
import { IS_ANDROID_WEB_VIEW } from '../../../util/is-android-web-view';
import { androidInterface } from '../android-interface';
import { selectScheduleWidgetData } from '../../work-context/store/work-context.selectors';
import { DroidLog } from '../../../core/log';
import { HydrationStateService } from '../../../op-log/apply/hydration-state.service';

/**
 * Key used in the native KeyValStore. Consumed by TodayTasksProvider's
 * `/schedule` URI path to expose the 7-day schedule snapshot to the
 * sp-today-widget app.
 */
export const ANDROID_WIDGET_SCHEDULE_KEY = 'schedule_widget_data';

/**
 * Pushes the current schedule snapshot (tomorrow's timed events plus 7-day
 * load bars) to the native KeyValStore whenever the relevant task state
 * changes.
 *
 * - Skipped during remote-op hydration to avoid thrashing on sync replay
 * - Debounced (300ms) to collapse rapid state updates
 * - distinctUntilChanged on JSON string to avoid redundant native calls
 */
@Injectable()
export class AndroidScheduleWidgetEffects {
  private _store = inject(Store);
  private _hydrationState = inject(HydrationStateService);

  pushScheduleToNative$ =
    IS_ANDROID_WEB_VIEW &&
    createEffect(
      () =>
        this._store.select(selectScheduleWidgetData).pipe(
          filter(() => !this._hydrationState.isApplyingRemoteOps()),
          debounceTime(300),
          distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
          tap(async (data) => {
            try {
              await androidInterface.saveToDbWrapped(
                ANDROID_WIDGET_SCHEDULE_KEY,
                JSON.stringify(data),
              );
              DroidLog.log('Pushed schedule to widget store', {
                tomorrowCount: data.tomorrow.events.length,
                weekDays: data.weekLoad.length,
              });
            } catch (e) {
              DroidLog.err('Failed to push schedule to widget store', e);
            }
          }),
        ),
      { dispatch: false },
    );
}
