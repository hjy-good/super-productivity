import { inject, Injectable } from '@angular/core';
import { createEffect } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { debounceTime, distinctUntilChanged, filter, tap } from 'rxjs/operators';
import { IS_ANDROID_WEB_VIEW } from '../../../util/is-android-web-view';
import { androidInterface } from '../android-interface';
import { selectTodayTasksForWidget } from '../../work-context/store/work-context.selectors';
import { DroidLog } from '../../../core/log';
import { HydrationStateService } from '../../../op-log/apply/hydration-state.service';

/**
 * Key used in the native KeyValStore. Consumed by TodayTasksProvider
 * (ContentProvider) to expose today's tasks to the sp-today-widget app.
 */
export const ANDROID_WIDGET_TODAY_KEY = 'today_tasks';

/**
 * Pushes the current TODAY task list to the native KeyValStore whenever it
 * changes. The native ContentProvider reads this key and serves it to the
 * separate sp-today-widget APK for lockscreen rendering.
 *
 * - Skipped during remote-op hydration to avoid thrashing on sync replay
 * - Debounced (300ms) to collapse rapid state updates
 * - distinctUntilChanged on JSON string to avoid redundant native calls
 */
@Injectable()
export class AndroidWidgetTodayEffects {
  private _store = inject(Store);
  private _hydrationState = inject(HydrationStateService);

  pushTodayTasksToNative$ =
    IS_ANDROID_WEB_VIEW &&
    createEffect(
      () =>
        this._store.select(selectTodayTasksForWidget).pipe(
          filter(() => !this._hydrationState.isApplyingRemoteOps()),
          debounceTime(300),
          distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
          tap(async (tasks) => {
            try {
              await androidInterface.saveToDbWrapped(
                ANDROID_WIDGET_TODAY_KEY,
                JSON.stringify(tasks),
              );
              DroidLog.log('Pushed today tasks to widget store', {
                count: tasks.length,
              });
            } catch (e) {
              DroidLog.err('Failed to push today tasks to widget store', e);
            }
          }),
        ),
      { dispatch: false },
    );
}
