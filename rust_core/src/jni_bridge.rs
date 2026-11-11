//! JNI C-ABI Boilerplate (`jni_bridge.rs`)
//!
//! Exposes `focus_fsm::step_focus_state_machine`, `focus_fsm::is_time_within_schedule_window`,
//! `notif_classifier::classify_and_evaluate_notification`, and `call_bouncer::evaluate_incoming_call`
//! to Android's `com.example.core.jni.SentinalRustJniNative` class via standard JNI C-ABI symbols.
//!
//! Uses zero-external-dependency raw JNI FFI types (`JNIEnv`, `jclass`, `jstring`, `jint`, `jlong`, `jboolean`)
//! so the crate compiles stand-alone with `cargo build --release` or Android NDK toolchains.

use std::ffi::{c_char, c_void, CStr, CString};

use crate::call_bouncer::{
    evaluate_incoming_call, normalize_caller_lookup_key, CallFilterAction, CallPolicyConfig,
};
use crate::focus_fsm::{
    is_time_within_schedule_window, step_focus_state_machine, EngineCommand, FocusEngineState,
    FocusEvent, FocusMode,
};
use crate::notif_classifier::{
    classify_and_evaluate_notification, FocusRuleAction, NotificationCategory, PriorityLevel,
    PriorityRuleTable,
};

// Minimal JNI C-ABI type definitions compatible with Android NDK <jni.h>
pub type jint = i32;
pub type jlong = i64;
pub type jboolean = u8;
pub type jclass = *mut c_void;
pub type jobject = *mut c_void;
pub type jstring = jobject;

pub const JNI_FALSE: jboolean = 0;
pub const JNI_TRUE: jboolean = 1;

#[repr(C)]
pub struct JNINativeInterface {
    _reserved: [*mut c_void; 167],
    pub new_string_utf: unsafe extern "system" fn(env: *mut JNIEnv, utf: *const c_char) -> jstring,
    pub get_string_utf_chars:
        unsafe extern "system" fn(env: *mut JNIEnv, str: jstring, is_copy: *mut jboolean) -> *const c_char,
    pub release_string_utf_chars:
        unsafe extern "system" fn(env: *mut JNIEnv, str: jstring, utf: *const c_char),
}

pub type JNIEnv = *const JNINativeInterface;

unsafe fn jstring_to_rust(env: *mut JNIEnv, input: jstring) -> String {
    if env.is_null() || input.is_null() {
        return String::new();
    }
    let iface = &*(*env);
    let mut is_copy: jboolean = JNI_FALSE;
    let ptr = (iface.get_string_utf_chars)(env, input, &mut is_copy);
    if ptr.is_null() {
        return String::new();
    }
    let rust_str = CStr::from_ptr(ptr).to_string_lossy().into_owned();
    (iface.release_string_utf_chars)(env, input, ptr);
    rust_str
}

unsafe fn rust_to_jstring(env: *mut JNIEnv, text: &str) -> jstring {
    if env.is_null() {
        return std::ptr::null_mut();
    }
    let iface = &*(*env);
    let sanitized = CString::new(text.replace('\0', "")).unwrap_or_default();
    (iface.new_string_utf)(env, sanitized.as_ptr())
}

fn focus_mode_from_ordinal(ordinal: jint) -> FocusMode {
    match ordinal {
        1 => FocusMode::DoNotDisturb,
        2 => FocusMode::Gaming,
        3 => FocusMode::Work,
        4 => FocusMode::Sleep,
        5 => FocusMode::Cinema,
        _ => FocusMode::Off,
    }
}

fn focus_mode_to_ordinal(mode: FocusMode) -> jint {
    match mode {
        FocusMode::Off => 0,
        FocusMode::DoNotDisturb => 1,
        FocusMode::Gaming => 2,
        FocusMode::Work => 3,
        FocusMode::Sleep => 4,
        FocusMode::Cinema => 5,
    }
}

fn priority_from_rank(rank: jint) -> Option<PriorityLevel> {
    match rank {
        4 => Some(PriorityLevel::Critical),
        3 => Some(PriorityLevel::High),
        2 => Some(PriorityLevel::Medium),
        1 => Some(PriorityLevel::Low),
        _ => None,
    }
}

fn priority_to_rank(priority: PriorityLevel) -> jint {
    match priority {
        PriorityLevel::Critical => 4,
        PriorityLevel::High => 3,
        PriorityLevel::Medium => 2,
        PriorityLevel::Low => 1,
    }
}

fn rule_action_from_ordinal(ordinal: jint) -> FocusRuleAction {
    match ordinal {
        0 => FocusRuleAction::AllowAll,
        1 => FocusRuleAction::MuteSilent,
        _ => FocusRuleAction::HideCompletely,
    }
}

fn rule_action_to_ordinal(action: FocusRuleAction) -> jint {
    match action {
        FocusRuleAction::AllowAll => 0,
        FocusRuleAction::MuteSilent => 1,
        FocusRuleAction::HideCompletely => 2,
    }
}

fn category_to_ordinal(cat: NotificationCategory) -> jint {
    match cat {
        NotificationCategory::SecurityAndUrgent => 0,
        NotificationCategory::DirectMessages => 1,
        NotificationCategory::WorkAndCalendar => 2,
        NotificationCategory::SocialAndCommunity => 3,
        NotificationCategory::NewsAndMedia => 4,
        NotificationCategory::PromotionsAndDeals => 5,
    }
}

// =============================================================================
// 1. JNI EXPORT: Crate Version & Health Check
//    Kotlin: external fun nativeCrateVersion(): String
// =============================================================================
#[no_mangle]
pub unsafe extern "system" fn Java_com_example_core_jni_SentinalRustJniNative_nativeCrateVersion(
    env: *mut JNIEnv,
    _class: jclass,
) -> jstring {
    rust_to_jstring(env, "sentinal_rust_core/1.0.0-jni")
}

// =============================================================================
// 2. JNI EXPORT: Focus State Machine Transition
//    Kotlin: external fun nativeStepFocusStateMachine(
//        currentModeOrdinal: Int,
//        previousModeBeforeGameOrdinal: Int,
//        sessionStartMs: Long,
//        expiresAtMs: Long,
//        autoGameDetectionEnabled: Boolean,
//        askBeforeGameActivation: Boolean,
//        isAutoTriggeredByGame: Boolean,
//        isAutoTriggeredByWork: Boolean,
//        isAutoTriggeredBySleep: Boolean,
//        activeGamePackage: String,
//        pendingGamePackage: String,
//        declinedGamePackageForSession: String,
//        manuallyDismissedGamingWhilstInGame: Boolean,
//        eventTypeOrdinal: Int,
//        eventTargetModeOrdinal: Int,
//        eventPackageName: String,
//        eventIsGame: Boolean,
//        eventDurationMinutes: Int,
//        nowMs: Long
//    ): String
//
//    Returns pipe-delimited wire payload:
//    "activeModeOrd|suppressInGame|declinedPkg|activeGamePkg|pendingPkg|commandsCsv|summary"
// =============================================================================
#[no_mangle]
pub unsafe extern "system" fn Java_com_example_core_jni_SentinalRustJniNative_nativeStepFocusStateMachine(
    env: *mut JNIEnv,
    _class: jclass,
    current_mode_ordinal: jint,
    prev_mode_before_game_ordinal: jint,
    session_start_ms: jlong,
    expires_at_ms: jlong,
    auto_game_detection_enabled: jboolean,
    ask_before_game_activation: jboolean,
    is_auto_triggered_by_game: jboolean,
    is_auto_triggered_by_work: jboolean,
    is_auto_triggered_by_sleep: jboolean,
    active_game_package: jstring,
    pending_game_package: jstring,
    declined_game_package_for_session: jstring,
    manually_dismissed_gaming_whilst_in_game: jboolean,
    event_type_ordinal: jint,
    event_target_mode_ordinal: jint,
    event_package_name: jstring,
    event_is_game: jboolean,
    event_duration_minutes: jint,
    now_ms: jlong,
) -> jstring {
    let active_pkg_str = jstring_to_rust(env, active_game_package);
    let pending_pkg_str = jstring_to_rust(env, pending_game_package);
    let declined_pkg_str = jstring_to_rust(env, declined_game_package_for_session);
    let event_pkg_str = jstring_to_rust(env, event_package_name);

    let state = FocusEngineState {
        active_mode: focus_mode_from_ordinal(current_mode_ordinal),
        previous_mode_before_game: focus_mode_from_ordinal(prev_mode_before_game_ordinal),
        session_start_ms: if session_start_ms > 0 {
            Some(session_start_ms)
        } else {
            None
        },
        expires_at_ms: if expires_at_ms > 0 {
            Some(expires_at_ms)
        } else {
            None
        },
        auto_game_detection_enabled: auto_game_detection_enabled != JNI_FALSE,
        ask_before_game_activation: ask_before_game_activation != JNI_FALSE,
        is_auto_triggered_by_game: is_auto_triggered_by_game != JNI_FALSE,
        is_auto_triggered_by_work: is_auto_triggered_by_work != JNI_FALSE,
        is_auto_triggered_by_sleep: is_auto_triggered_by_sleep != JNI_FALSE,
        active_game_package: if active_pkg_str.is_empty() {
            None
        } else {
            Some(active_pkg_str)
        },
        pending_game_package: if pending_pkg_str.is_empty() {
            None
        } else {
            Some(pending_pkg_str)
        },
        pending_game_countdown_sec: None,
        declined_game_package_for_session: if declined_pkg_str.is_empty() {
            None
        } else {
            Some(declined_pkg_str)
        },
        manually_dismissed_gaming_whilst_in_game: manually_dismissed_gaming_whilst_in_game != JNI_FALSE,
    };

    let event = match event_type_ordinal {
        0 => FocusEvent::ManualSetMode {
            target_mode: focus_mode_from_ordinal(event_target_mode_ordinal),
            duration_minutes: if event_duration_minutes > 0 {
                Some(event_duration_minutes as u32)
            } else {
                None
            },
            now_ms,
        },
        1 => FocusEvent::QuickTileToggle {
            tile_mode: focus_mode_from_ordinal(event_target_mode_ordinal),
            now_ms,
        },
        2 => FocusEvent::ForegroundAppObserved {
            package_name: event_pkg_str,
            is_game: event_is_game != JNI_FALSE,
            now_ms,
        },
        3 => FocusEvent::ConfirmPendingGamePrompt { now_ms },
        _ => FocusEvent::DeclinePendingGamePrompt,
    };

    let outcome = step_focus_state_machine(&state, event);

    let commands_csv: Vec<String> = outcome
        .commands
        .iter()
        .map(|cmd| match cmd {
            EngineCommand::SyncInterruptionFilter(m) => {
                format!("SyncInterruptionFilter:{}", focus_mode_to_ordinal(*m))
            }
            EngineCommand::StartForegroundShield(m) => {
                format!("StartForegroundShield:{}", focus_mode_to_ordinal(*m))
            }
            EngineCommand::StopForegroundShield => "StopForegroundShield".to_string(),
            EngineCommand::ShowGameConfirmPrompt {
                package_name,
                countdown_seconds,
            } => format!("ShowGameConfirmPrompt:{}:{}", package_name, countdown_seconds),
            EngineCommand::DismissGameConfirmPrompt => "DismissGameConfirmPrompt".to_string(),
            EngineCommand::ShowGamingActivatedHud { package_name } => {
                format!("ShowGamingActivatedHud:{}", package_name)
            }
            EngineCommand::SyncQuickSettingsTiles => "SyncQuickSettingsTiles".to_string(),
            EngineCommand::LogCompletedSessionToRoom {
                mode,
                duration_minutes,
                trigger_source,
            } => format!(
                "LogCompletedSessionToRoom:{}:{}:{}",
                focus_mode_to_ordinal(*mode),
                duration_minutes,
                trigger_source
            ),
        })
        .collect();

    let wire = format!(
        "{}|{}|{}|{}|{}|{}|{}",
        focus_mode_to_ordinal(outcome.state.active_mode),
        if outcome.state.manually_dismissed_gaming_whilst_in_game {
            1
        } else {
            0
        },
        outcome
            .state
            .declined_game_package_for_session
            .unwrap_or_default(),
        outcome.state.active_game_package.unwrap_or_default(),
        outcome.state.pending_game_package.unwrap_or_default(),
        commands_csv.join(","),
        outcome.summary.replace('|', "/")
    );

    rust_to_jstring(env, &wire)
}

// =============================================================================
// 3. JNI EXPORT: Schedule Window Evaluator
//    Kotlin: external fun nativeEvaluateScheduleWindow(...): Boolean
// =============================================================================
#[no_mangle]
pub unsafe extern "system" fn Java_com_example_core_jni_SentinalRustJniNative_nativeEvaluateScheduleWindow(
    _env: *mut JNIEnv,
    _class: jclass,
    start_hour: jint,
    start_minute: jint,
    end_hour: jint,
    end_minute: jint,
    current_day_of_week: jint,
    yesterday_day_of_week: jint,
    active_days_mask: jint,
    current_hour: jint,
    current_minute: jint,
) -> jboolean {
    let active = is_time_within_schedule_window(
        start_hour.clamp(0, 23) as u8,
        start_minute.clamp(0, 59) as u8,
        end_hour.clamp(0, 23) as u8,
        end_minute.clamp(0, 59) as u8,
        current_day_of_week.clamp(1, 7) as u8,
        yesterday_day_of_week.clamp(1, 7) as u8,
        (active_days_mask & 0xFF) as u8,
        current_hour.clamp(0, 23) as u8,
        current_minute.clamp(0, 59) as u8,
    );
    if active {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

// =============================================================================
// 4. JNI EXPORT: Notification Priority Classifier & Focus Rules Evaluator
//    Kotlin: external fun nativeClassifyNotification(
//        packageName: String,
//        title: String,
//        body: String,
//        criticalActionOrd: Int,
//        highActionOrd: Int,
//        mediumActionOrd: Int,
//        lowActionOrd: Int,
//        smartUrgentBreakthroughEnabled: Boolean,
//        categoryPriorityOverrideRank: Int
//    ): String
//
//    Returns pipe-delimited wire payload:
//    "categoryOrd|priorityRank|actionOrd|matchedUrgentBreakthrough|reason"
// =============================================================================
#[no_mangle]
pub unsafe extern "system" fn Java_com_example_core_jni_SentinalRustJniNative_nativeClassifyNotification(
    env: *mut JNIEnv,
    _class: jclass,
    package_name: jstring,
    title: jstring,
    body: jstring,
    critical_action_ord: jint,
    high_action_ord: jint,
    medium_action_ord: jint,
    low_action_ord: jint,
    smart_urgent_breakthrough_enabled: jboolean,
    category_priority_override_rank: jint,
) -> jstring {
    let pkg_str = jstring_to_rust(env, package_name);
    let title_str = jstring_to_rust(env, title);
    let body_str = jstring_to_rust(env, body);

    let rules = PriorityRuleTable {
        critical_action: rule_action_from_ordinal(critical_action_ord),
        high_action: rule_action_from_ordinal(high_action_ord),
        medium_action: rule_action_from_ordinal(medium_action_ord),
        low_action: rule_action_from_ordinal(low_action_ord),
        smart_urgent_breakthrough_enabled: smart_urgent_breakthrough_enabled != JNI_FALSE,
    };

    let override_prio = priority_from_rank(category_priority_override_rank);
    let outcome = classify_and_evaluate_notification(
        &pkg_str,
        &title_str,
        &body_str,
        &rules,
        override_prio,
    );

    let wire = format!(
        "{}|{}|{}|{}|{}",
        category_to_ordinal(outcome.category),
        priority_to_rank(outcome.priority),
        rule_action_to_ordinal(outcome.action),
        if outcome.matched_urgent_breakthrough { 1 } else { 0 },
        outcome.reason
    );

    rust_to_jstring(env, &wire)
}

// =============================================================================
// 5. JNI EXPORT: Call Bouncer Cooldown & Repeat-Caller Matcher
//    Kotlin: external fun nativeNormalizeCallerKey(rawCaller: String): String
// =============================================================================
#[no_mangle]
pub unsafe extern "system" fn Java_com_example_core_jni_SentinalRustJniNative_nativeNormalizeCallerKey(
    env: *mut JNIEnv,
    _class: jclass,
    raw_caller: jstring,
) -> jstring {
    let raw = jstring_to_rust(env, raw_caller);
    let norm = normalize_caller_lookup_key(&raw);
    rust_to_jstring(env, &norm)
}

#[no_mangle]
pub unsafe extern "system" fn Java_com_example_core_jni_SentinalRustJniNative_nativeEvaluateIncomingCall(
    env: *mut JNIEnv,
    _class: jclass,
    raw_caller: jstring,
    is_starred_contact: jboolean,
    last_incoming_call_ms: jlong,
    last_outgoing_call_ms: jlong,
    last_auto_reply_ms: jlong,
    now_ms: jlong,
    decline_all_calls_regardless: jboolean,
    strict_gaming_lockdown: jboolean,
    is_gaming_mode_active: jboolean,
    allow_starred_contacts: jboolean,
    allow_repeat_callers_within_3m: jboolean,
    auto_reply_enabled: jboolean,
) -> jstring {
    let raw = jstring_to_rust(env, raw_caller);
    let policy = CallPolicyConfig {
        decline_all_calls_regardless: decline_all_calls_regardless != JNI_FALSE,
        strict_gaming_lockdown: strict_gaming_lockdown != JNI_FALSE,
        is_gaming_mode_active: is_gaming_mode_active != JNI_FALSE,
        allow_starred_contacts: allow_starred_contacts != JNI_FALSE,
        allow_repeat_callers_within_3m: allow_repeat_callers_within_3m != JNI_FALSE,
        auto_reply_enabled: auto_reply_enabled != JNI_FALSE,
    };

    let decision = evaluate_incoming_call(
        &raw,
        is_starred_contact != JNI_FALSE,
        if last_incoming_call_ms > 0 {
            Some(last_incoming_call_ms)
        } else {
            None
        },
        if last_outgoing_call_ms > 0 {
            Some(last_outgoing_call_ms)
        } else {
            None
        },
        if last_auto_reply_ms > 0 {
            Some(last_auto_reply_ms)
        } else {
            None
        },
        now_ms,
        &policy,
    );

    let action_ord = match decision.filter_action {
        CallFilterAction::AllowRingBreakthrough => 0,
        CallFilterAction::BounceAndDeclineCall => 1,
    };

    let wire = format!(
        "{}|{}|{}|{}|{}|{}",
        decision.normalized_caller_key,
        action_ord,
        if decision.dispatch_auto_reply { 1 } else { 0 },
        decision.updated_last_call_timestamp_ms.unwrap_or(0),
        decision.updated_last_auto_reply_timestamp_ms.unwrap_or(0),
        decision.reason
    );

    rust_to_jstring(env, &wire)
}
