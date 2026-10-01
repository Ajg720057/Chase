# Mirrors the checklist data in onboarding-checklist.html so the PDFs and the
# web tool stay in sync. offsetDays: calendar days added to the start date.
# Day label = offsetDays + 1 (offsetDays 0 == "Day 1", the start date itself).

MANAGER_PHASES = [
    {"id": "pre2", "label": "2 Weeks Out", "window": "T-14 to T-8"},
    {"id": "pre1", "label": "1 Week Out", "window": "T-7 to T-3"},
    {"id": "prefin", "label": "Final 48 Hours", "window": "T-2 to T-1"},
    {"id": "day1", "label": "Day 1", "window": ""},
    {"id": "week1", "label": "Week 1", "window": "Days 2-7"},
    {"id": "month1", "label": "Weeks 2-4", "window": "Days 8-30"},
    {"id": "month2", "label": "Days 31-60", "window": ""},
    {"id": "month3", "label": "Days 61-90", "window": ""},
]

MANAGER_ITEMS = [
    ("m01", "pre2", -14, "Confirm offer accepted and start date is locked"),
    ("m02", "pre2", -14, "Request equipment (laptop, monitor, peripherals) from IT"),
    ("m03", "pre2", -13, "Open IT ticket for accounts: email, SSO, core systems"),
    ("m04", "pre2", -12, "Notify HR / payroll to begin benefits and payroll setup"),
    ("m05", "pre2", -10, "Draft the 30/60/90-day plan"),
    ("m06", "pre1", -7, "Assign an onboarding buddy"),
    ("m07", "pre1", -7, "Reserve desk, or confirm remote workstation shipping address"),
    ("m08", "pre1", -6, "Block out Week 1 calendar: intros, training, 1:1s"),
    ("m09", "pre1", -5, "Share the 30/60/90-day plan with the team"),
    ("m10", "pre1", -4, "Order building badge / access card"),
    ("m11", "prefin", -2, "Send welcome email with first-day logistics"),
    ("m12", "prefin", -1, "Confirm accounts are active and equipment has arrived"),
    ("m13", "prefin", -1, "Draft team announcement"),
    ("m14", "day1", 0, "Greet new hire; walk through facilities and access"),
    ("m15", "day1", 0, "Hold first 1:1 - role expectations and the 30/60/90 plan"),
    ("m16", "day1", 0, "Send the team announcement"),
    ("m17", "day1", 0, "Confirm workstation, accounts, and tools all work"),
    ("m18", "week1", 2, "Mid-week check-in - surface early blockers"),
    ("m19", "week1", 4, "Verify required compliance / security training is scheduled"),
    ("m20", "week1", 6, "End-of-week-1 1:1 - feedback and open questions"),
    ("m21", "month1", 13, "2-week check-in - review early performance"),
    ("m22", "month1", 20, "Assign first meaningful project or task"),
    ("m23", "month1", 29, "30-day review - assess progress, set 60-day goals"),
    ("m24", "month2", 44, "Mid-point check-in"),
    ("m25", "month2", 59, "60-day review - evaluate progress, adjust training plan"),
    ("m26", "month3", 74, "Check in ahead of the 90-day review"),
    ("m27", "month3", 89, "90-day review - formal evaluation and path forward"),
    ("m28", "month3", 89, "Collect the new hire's feedback on their onboarding"),
]

NEWHIRE_PHASES = [
    {"id": "day1", "label": "Day 1", "window": ""},
    {"id": "week1", "label": "Week 1", "window": "Days 2-7"},
    {"id": "month1", "label": "Weeks 2-4", "window": "Days 8-30"},
    {"id": "month2", "label": "Days 31-60", "window": ""},
    {"id": "month3", "label": "Days 61-90", "window": ""},
]

NEWHIRE_ITEMS = [
    ("n01", "day1", 0, "Complete new-hire paperwork (I-9, tax forms, benefits elections)"),
    ("n02", "day1", 0, "Log into email, chat, and core systems"),
    ("n03", "day1", 0, "Meet your manager for a first 1:1"),
    ("n04", "day1", 0, "Meet your immediate team"),
    ("n05", "day1", 0, "Review your 30/60/90-day plan"),
    ("n06", "week1", 1, "Complete required compliance and security training"),
    ("n07", "week1", 2, "Set up your workstation and dev / work environment"),
    ("n08", "week1", 3, "Read the team handbook and key documentation"),
    ("n09", "week1", 4, "Shadow a team meeting or customer call"),
    ("n10", "week1", 6, "Schedule 1:1s with key cross-functional partners"),
    ("n11", "month1", 9, "Complete role-specific training or certification"),
    ("n12", "month1", 13, "Attend your 2-week check-in"),
    ("n13", "month1", 20, "Take on your first small task or project"),
    ("n14", "month1", 27, "Share informal feedback on your onboarding so far"),
    ("n15", "month1", 29, "Attend your 30-day review"),
    ("n16", "month2", 39, "Take ownership of a project with less oversight"),
    ("n17", "month2", 49, "Deepen product / domain knowledge"),
    ("n18", "month2", 59, "Attend your 60-day review - self-assess against goals"),
    ("n19", "month3", 69, "Contribute independently to a team goal"),
    ("n20", "month3", 79, "Identify growth areas and draft next-quarter goals"),
    ("n21", "month3", 89, "Attend your 90-day review"),
    ("n22", "month3", 89, "Complete the onboarding feedback survey"),
]


def day_label(offset_days):
    if offset_days >= 0:
        return "Day " + str(offset_days + 1)
    return str(abs(offset_days)) + "d before Day 1"
