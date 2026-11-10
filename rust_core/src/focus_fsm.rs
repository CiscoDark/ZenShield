//! Engine 1: Deterministic Focus State Machine & Schedule Evaluation Engine
//!
//! Models Sentinal's Focus Profile transitions, 5-second game launch confirmation prompt,
//! per-session manual turn-off suppression, and same-day/overnight schedule windows
//! as a pure algebraic state machine (`State + Event -> TransitionOutcome`).

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum FocusMode {
    Off = 0,
    DoNotDisturb = 1,
    Gaming = 2,
    Work = 3,
    Sleep = 4,
    Cinema = 5,
}

impl FocusMode {
    pub fn display_name(self) -> &'static str {
        match self {
            FocusMode::Off => "Off",
            FocusMode::DoNotDisturb => "Do Not Disturb",
            FocusMode::Gaming => "Gaming",
            FocusMode::Work => "Deep Work",
            FocusMode::Sleep => "Sleep",
            FocusMode::Cinema => "Cinema",
        }
    }

    pub fn next_cycle(self) -> FocusMode {
        match self {
            FocusMode::DoNotDisturb => FocusMode::Gaming,
            FocusMode::Gaming => FocusMode::Work,
            FocusMode::Work => FocusMode::Sleep,
            FocusMode::Sleep => FocusMode::DoNotDisturb,
            FocusMode::Cinema => FocusMode::DoNotDisturb,
            FocusMode::Off => FocusMode::DoNotDisturb,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ScheduleConfig {
    pub enabled: bool,
    pub start_hour: u8,
    pub start_minute: u8,
    pub end_hour: u8,
    pub end_minute: u8,
    /// Bitmask of active days (1 << Calendar.DAY_OF_WEEK, 1..=7)
    pub active_days_mask: u8,
    pub dismissed_window_start_ms: Option<i64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FocusEngineState {
    pub active_mode: FocusMode,
    pub previous_mode_before_game: FocusMode,
    pub session_start_ms: Option<i64>,
    pub expires_at_ms: Option<i64>,
    pub auto_game_detection_enabled: bool,
    pub ask_before_game_activation: bool,
    pub is_auto_triggered_by_game: bool,
    pub is_auto_triggered_by_work: bool,
    pub is_auto_triggered_by_sleep: bool,
    pub active_game_package: Option<String>,
    pub pending_game_package: Option<String>,
    pub pending_game_countdown_sec: Option<u8>,
    pub declined_game_package_for_session: Option<String>,
    pub manually_dismissed_gaming_whilst_in_game: bool,
}

impl Default for FocusEngineState {
    fn default() -> Self {
        Self {
            active_mode: FocusMode::Off,
            previous_mode_before_game: FocusMode::Off,
            session_start_ms: None,
            expires_at_ms: None,
            auto_game_detection_enabled: true,
            ask_before_game_activation: true,
            is_auto_triggered_by_game: false,
            is_auto_triggered_by_work: false,
            is_auto_triggered_by_sleep: false,
            active_game_package: None,
            pending_game_package: None,
            pending_game_countdown_sec: None,
            declined_game_package_for_session: None,
            manually_dismissed_gaming_whilst_in_game: false,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FocusEvent {
    ManualSetMode {
        target_mode: FocusMode,
        duration_minutes: Option<u32>,
        now_ms: i64,
    },
    QuickTileToggle {
        tile_mode: FocusMode,
        now_ms: i64,
    },
    ForegroundAppObserved {
        package_name: String,
        is_game: bool,
        now_ms: i64,
    },
    ConfirmPendingGamePrompt {
        now_ms: i64,
    },
    DeclinePendingGamePrompt,
    EvaluateScheduleBoundary {
        schedule_mode: FocusMode,
        in_active_window: bool,
        window_start_ms: i64,
        window_end_ms: i64,
        dismissed_window_start_ms: Option<i64>,
        now_ms: i64,
    },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EngineCommand {
    SyncInterruptionFilter(FocusMode),
    StartForegroundShield(FocusMode),
    StopForegroundShield,
    ShowGameConfirmPrompt {
        package_name: String,
        countdown_seconds: u8,
    },
    DismissGameConfirmPrompt,
    ShowGamingActivatedHud {
        package_name: String,
    },
    SyncQuickSettingsTiles,
    LogCompletedSessionToRoom {
        mode: FocusMode,
        duration_minutes: u32,
        trigger_source: &'static str,
    },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TransitionOutcome {
    pub state: FocusEngineState,
    pub commands: Vec<EngineCommand>,
    pub summary: String,
}

/// Evaluates whether `current_mins_of_day` falls within `[start_mins, end_mins)`
/// supporting both same-day (09:00..17:00) and overnight (22:30..07:00) windows.
pub fn is_time_within_schedule_window(
    start_hour: u8,
    start_minute: u8,
    end_hour: u8,
    end_minute: u8,
    current_day_of_week: u8,
    yesterday_day_of_week: u8,
    active_days_mask: u8,
    current_hour: u8,
    current_minute: u8,
) -> bool {
    let start_total = (start_hour as u16) * 60 + (start_minute as u16);
    let end_total = (end_hour as u16) * 60 + (end_minute as u16);
    if start_total == end_total {
        return false;
    }
    let current_total = (current_hour as u16) * 60 + (current_minute as u16);
    let today_bit = 1u8 << (current_day_of_week.min(7));
    let yesterday_bit = 1u8 << (yesterday_day_of_week.min(7));

    if start_total < end_total {
        // Same-day window (e.g. 09:00 to 17:00)
        (active_days_mask & today_bit != 0)
            && current_total >= start_total
            && current_total < end_total
    } else {
        // Overnight window (e.g. 22:30 to 07:00)
        if current_total >= start_total {
            (active_days_mask & today_bit) != 0
        } else if current_total < end_total {
            (active_days_mask & yesterday_bit) != 0
        } else {
            false
        }
    }
}

pub fn step_focus_state_machine(
    current: &FocusEngineState,
    event: FocusEvent,
) -> TransitionOutcome {
    let mut next = current.clone();
    let mut commands = Vec::new();

    match event {
        FocusEvent::ManualSetMode {
            target_mode,
            duration_minutes,
            now_ms,
        } => {
            let was_gaming = current.active_mode == FocusMode::Gaming && target_mode != FocusMode::Gaming;
            let had_pending = current.pending_game_package.is_some() && target_mode != FocusMode::Gaming;

            if current.active_mode != FocusMode::Off && current.active_mode != target_mode {
                let started = current.session_start_ms.unwrap_or(now_ms - 60_000);
                let elapsed_mins = (((now_ms - started) / 60_000).max(1)) as u32;
                let source = if current.is_auto_triggered_by_game {
                    "Auto Game Detector"
                } else if current.is_auto_triggered_by_work || current.is_auto_triggered_by_sleep {
                    "Scheduled"
                } else {
                    "Manual"
                };
                commands.push(EngineCommand::LogCompletedSessionToRoom {
                    mode: current.active_mode,
                    duration_minutes: elapsed_mins,
                    trigger_source: source,
                });
            }

            if was_gaming || had_pending {
                next.declined_game_package_for_session = current
                    .active_game_package
                    .clone()
                    .or_else(|| current.pending_game_package.clone())
                    .or_else(|| current.declined_game_package_for_session.clone());
                next.manually_dismissed_gaming_whilst_in_game = true;
            } else if target_mode == FocusMode::Gaming {
                next.declined_game_package_for_session = None;
                next.manually_dismissed_gaming_whilst_in_game = false;
            }

            next.active_mode = target_mode;
            next.pending_game_package = None;
            next.pending_game_countdown_sec = None;
            next.is_auto_triggered_by_game = false;
            next.is_auto_triggered_by_work = false;
            next.is_auto_triggered_by_sleep = false;
            next.session_start_ms = if target_mode != FocusMode::Off {
                Some(now_ms)
            } else {
                None
            };
            next.expires_at_ms = if target_mode != FocusMode::Off {
                duration_minutes.map(|m| now_ms + (m as i64) * 60_000)
            } else {
                None
            };

            commands.push(EngineCommand::DismissGameConfirmPrompt);
            commands.push(EngineCommand::SyncInterruptionFilter(target_mode));
            if target_mode != FocusMode::Off {
                commands.push(EngineCommand::StartForegroundShield(target_mode));
            } else {
                commands.push(EngineCommand::StopForegroundShield);
            }
            commands.push(EngineCommand::SyncQuickSettingsTiles);

            let summary = format!(
                "ManualSetMode -> {:?} (suppress_in_game={})",
                target_mode, next.manually_dismissed_gaming_whilst_in_game
            );
            TransitionOutcome {
                state: next,
                commands,
                summary,
            }
        }

        FocusEvent::QuickTileToggle { tile_mode, now_ms } => {
            let target = if current.active_mode == tile_mode {
                FocusMode::Off
            } else {
                tile_mode
            };
            step_focus_state_machine(
                current,
                FocusEvent::ManualSetMode {
                    target_mode: target,
                    duration_minutes: None,
                    now_ms,
                },
            )
        }

        FocusEvent::ForegroundAppObserved {
            package_name,
            is_game,
            now_ms,
        } => {
            if is_game {
                next.active_game_package = Some(package_name.clone());
                let suppressed = current.manually_dismissed_gaming_whilst_in_game
                    || current
                        .declined_game_package_for_session
                        .as_deref()
                        == Some(package_name.as_str());

                if !current.auto_game_detection_enabled
                    || current.active_mode == FocusMode::Gaming
                    || suppressed
                {
                    let summary = format!(
                        "GameObserved({}) -> Suppressed (in_game_dismissed={})",
                        package_name, suppressed
                    );
                    return TransitionOutcome {
                        state: next,
                        commands,
                        summary,
                    };
                }

                if current.ask_before_game_activation {
                    next.pending_game_package = Some(package_name.clone());
                    next.pending_game_countdown_sec = Some(5);
                    commands.push(EngineCommand::ShowGameConfirmPrompt {
                        package_name: package_name.clone(),
                        countdown_seconds: 5,
                    });
                    let summary = format!(
                        "GameObserved({}) -> Prompting 5s Confirmation",
                        package_name
                    );
                    TransitionOutcome {
                        state: next,
                        commands,
                        summary,
                    }
                } else {
                    next.previous_mode_before_game = current.active_mode;
                    next.active_mode = FocusMode::Gaming;
                    next.is_auto_triggered_by_game = true;
                    next.session_start_ms = Some(now_ms);
                    commands.push(EngineCommand::SyncInterruptionFilter(FocusMode::Gaming));
                    commands.push(EngineCommand::StartForegroundShield(FocusMode::Gaming));
                    commands.push(EngineCommand::ShowGamingActivatedHud {
                        package_name: package_name.clone(),
                    });
                    commands.push(EngineCommand::SyncQuickSettingsTiles);
                    let summary = format!(
                        "GameObserved({}) -> Auto-Activated Gaming Mode",
                        package_name
                    );
                    TransitionOutcome {
                        state: next,
                        commands,
                        summary,
                    }
                }
            } else {
                // Non-game foreground package observed: clear per-session game suppression if not systemui
                if package_name != "com.android.systemui" && package_name != "android" {
                    next.declined_game_package_for_session = None;
                    next.manually_dismissed_gaming_whilst_in_game = false;
                    next.active_game_package = None;
                }
                let summary = format!(
                    "ForegroundNonGame({}) -> Session suppression reset",
                    package_name
                );
                TransitionOutcome {
                    state: next,
                    commands,
                    summary,
                }
            }
        }

        FocusEvent::ConfirmPendingGamePrompt { now_ms } => {
            let pkg = current
                .pending_game_package
                .clone()
                .or_else(|| current.active_game_package.clone())
                .unwrap_or_else(|| "GameSession".to_string());
            next.previous_mode_before_game = current.active_mode;
            next.active_mode = FocusMode::Gaming;
            next.is_auto_triggered_by_game = true;
            next.session_start_ms = Some(now_ms);
            next.pending_game_package = None;
            next.pending_game_countdown_sec = None;
            next.active_game_package = Some(pkg.clone());
            next.declined_game_package_for_session = None;
            next.manually_dismissed_gaming_whilst_in_game = false;

            commands.push(EngineCommand::DismissGameConfirmPrompt);
            commands.push(EngineCommand::SyncInterruptionFilter(FocusMode::Gaming));
            commands.push(EngineCommand::StartForegroundShield(FocusMode::Gaming));
            commands.push(EngineCommand::ShowGamingActivatedHud { package_name: pkg });
            commands.push(EngineCommand::SyncQuickSettingsTiles);

            TransitionOutcome {
                state: next,
                commands,
                summary: "ConfirmPendingGamePrompt -> Gaming Active".to_string(),
            }
        }

        FocusEvent::DeclinePendingGamePrompt => {
            next.declined_game_package_for_session = current
                .pending_game_package
                .clone()
                .or_else(|| current.active_game_package.clone());
            next.manually_dismissed_gaming_whilst_in_game = true;
            next.pending_game_package = None;
            next.pending_game_countdown_sec = None;
            commands.push(EngineCommand::DismissGameConfirmPrompt);

            TransitionOutcome {
                state: next,
                commands,
                summary: "DeclinePendingGamePrompt -> Suppressed until game exit".to_string(),
            }
        }

        FocusEvent::EvaluateScheduleBoundary {
            schedule_mode,
            in_active_window,
            window_start_ms,
            window_end_ms,
            dismissed_window_start_ms,
            now_ms,
        } => {
            if in_active_window {
                if dismissed_window_start_ms == Some(window_start_ms) {
                    return TransitionOutcome {
                        state: next,
                        commands,
                        summary: "Schedule window manually snoozed by user".to_string(),
                    };
                }
                if current.active_mode == FocusMode::Off {
                    next.active_mode = schedule_mode;
                    next.session_start_ms = Some(now_ms);
                    next.expires_at_ms = Some(window_end_ms);
                    next.is_auto_triggered_by_work = schedule_mode == FocusMode::Work;
                    next.is_auto_triggered_by_sleep = schedule_mode == FocusMode::Sleep;
                    commands.push(EngineCommand::SyncInterruptionFilter(schedule_mode));
                    commands.push(EngineCommand::StartForegroundShield(schedule_mode));
                    commands.push(EngineCommand::SyncQuickSettingsTiles);
                    return TransitionOutcome {
                        state: next,
                        commands,
                        summary: format!("Schedule engaged {:?}", schedule_mode),
                    };
                }
            } else {
                let is_matching_auto = (schedule_mode == FocusMode::Work
                    && current.active_mode == FocusMode::Work
                    && current.is_auto_triggered_by_work)
                    || (schedule_mode == FocusMode::Sleep
                        && current.active_mode == FocusMode::Sleep
                        && current.is_auto_triggered_by_sleep);
                if is_matching_auto {
                    return step_focus_state_machine(
                        current,
                        FocusEvent::ManualSetMode {
                            target_mode: FocusMode::Off,
                            duration_minutes: None,
                            now_ms,
                        },
                    );
                }
            }
            TransitionOutcome {
                state: next,
                commands,
                summary: "Schedule evaluated -> No transition required".to_string(),
            }
        }
    }
}
