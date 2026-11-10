//! Engine 3: Call Bouncer Cooldown & Repeat-Caller Matcher
//!
//! Normalizes cellular E.164 numbers and VoIP handles, evaluates 3-minute (180s)
//! repeat-caller emergency breakthroughs, guards against sending auto-replies to
//! recipients the user recently dialed (600s window), and enforces the 3-minute
//! per-caller auto-reply cooldown.

pub const REPEAT_CALLER_WINDOW_MS: i64 = 180_000;     // 3 minutes
pub const AUTO_REPLY_COOLDOWN_MS: i64 = 180_000;      // 3 minutes
pub const OUTGOING_CALL_EXEMPTION_MS: i64 = 600_000;  // 10 minutes

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CallPolicyConfig {
    pub decline_all_calls_regardless: bool,
    pub strict_gaming_lockdown: bool,
    pub is_gaming_mode_active: bool,
    pub allow_starred_contacts: bool,
    pub allow_repeat_callers_within_3m: bool,
    pub auto_reply_enabled: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CallFilterAction {
    AllowRingBreakthrough,
    BounceAndDeclineCall,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CallBounceDecision {
    pub normalized_caller_key: String,
    pub filter_action: CallFilterAction,
    pub dispatch_auto_reply: bool,
    pub updated_last_call_timestamp_ms: Option<i64>,
    pub updated_last_auto_reply_timestamp_ms: Option<i64>,
    pub reason: &'static str,
}

/// Normalizes a raw caller phone number or VoIP display handle into a canonical lookup key.
/// If 7 or more digits are present, extracts the trailing 9 digits so +1 / 0trunk prefixes match.
pub fn normalize_caller_lookup_key(raw: &str) -> String {
    let trimmed = raw.trim().to_ascii_lowercase();
    let digits: String = trimmed.chars().filter(|c| c.is_ascii_digit()).collect();
    if digits.len() >= 7 {
        let start = digits.len().saturating_sub(9);
        return digits[start..].to_string();
    }
    trimmed
}

pub fn evaluate_incoming_call(
    raw_caller: &str,
    is_starred_contact: bool,
    last_incoming_call_ms: Option<i64>,
    last_outgoing_call_to_caller_ms: Option<i64>,
    last_auto_reply_to_caller_ms: Option<i64>,
    now_ms: i64,
    policy: &CallPolicyConfig,
) -> CallBounceDecision {
    let key = normalize_caller_lookup_key(raw_caller);

    // 1. Zero-tolerance Lockdown check
    if policy.decline_all_calls_regardless {
        let send_reply = should_dispatch_auto_reply(
            &key,
            last_outgoing_call_to_caller_ms,
            last_auto_reply_to_caller_ms,
            now_ms,
            policy.auto_reply_enabled,
        );
        return CallBounceDecision {
            normalized_caller_key: key,
            filter_action: CallFilterAction::BounceAndDeclineCall,
            dispatch_auto_reply: send_reply,
            updated_last_call_timestamp_ms: Some(now_ms),
            updated_last_auto_reply_timestamp_ms: if send_reply {
                Some(now_ms)
            } else {
                last_auto_reply_to_caller_ms
            },
            reason: "Decline All Calls Regardless lockdown active",
        };
    }

    // 2. Starred Contact bypass (unless overridden by Strict Gaming Lockdown during Gaming Mode)
    let gaming_overrides_starred = policy.is_gaming_mode_active && policy.strict_gaming_lockdown;
    if is_starred_contact && policy.allow_starred_contacts && !gaming_overrides_starred {
        return CallBounceDecision {
            normalized_caller_key: key,
            filter_action: CallFilterAction::AllowRingBreakthrough,
            dispatch_auto_reply: false,
            updated_last_call_timestamp_ms: None,
            updated_last_auto_reply_timestamp_ms: last_auto_reply_to_caller_ms,
            reason: "Starred priority contact allowed through shield",
        };
    }

    // 3. 3-Minute (180s) Repeat Caller Emergency Breakthrough
    if policy.allow_repeat_callers_within_3m && !key.is_empty() && key != "unknown" {
        if let Some(prev_call_ms) = last_incoming_call_ms {
            if prev_call_ms > 0 && (now_ms - prev_call_ms) <= REPEAT_CALLER_WINDOW_MS {
                return CallBounceDecision {
                    normalized_caller_key: key,
                    filter_action: CallFilterAction::AllowRingBreakthrough,
                    dispatch_auto_reply: false,
                    updated_last_call_timestamp_ms: None, // Consume repeat window on breakthrough
                    updated_last_auto_reply_timestamp_ms: last_auto_reply_to_caller_ms,
                    reason: "Second call within 180s repeat-caller emergency window",
                };
            }
        }
    }

    // 4. Bounce call & evaluate 3-minute auto-reply cooldown + 10-minute outgoing call guard
    let send_reply = should_dispatch_auto_reply(
        &key,
        last_outgoing_call_to_caller_ms,
        last_auto_reply_to_caller_ms,
        now_ms,
        policy.auto_reply_enabled,
    );

    CallBounceDecision {
        normalized_caller_key: key,
        filter_action: CallFilterAction::BounceAndDeclineCall,
        dispatch_auto_reply: send_reply,
        updated_last_call_timestamp_ms: Some(now_ms),
        updated_last_auto_reply_timestamp_ms: if send_reply {
            Some(now_ms)
        } else {
            last_auto_reply_to_caller_ms
        },
        reason: if send_reply {
            "Call bounced • Auto-reply dispatched (3m cooldown started)"
        } else {
            "Call bounced silently (auto-reply suppressed by cooldown or outgoing guard)"
        },
    }
}

pub fn should_dispatch_auto_reply(
    normalized_key: &str,
    last_outgoing_call_ms: Option<i64>,
    last_auto_reply_ms: Option<i64>,
    now_ms: i64,
    auto_reply_enabled: bool,
) -> bool {
    if !auto_reply_enabled || normalized_key.is_empty() || normalized_key == "unknown" {
        return false;
    }
    if let Some(out_ms) = last_outgoing_call_ms {
        if out_ms > 0 && (now_ms - out_ms) <= OUTGOING_CALL_EXEMPTION_MS {
            return false;
        }
    }
    if let Some(reply_ms) = last_auto_reply_ms {
        if reply_ms > 0 && (now_ms - reply_ms) <= AUTO_REPLY_COOLDOWN_MS {
            return false;
        }
    }
    true
}
