package pt.isec.amov.tp.enums
import pt.isec.amov.tp.R


enum class RuleType(val labelRes: Int) {
    FALL_DETECTION(R.string.rule_fall_detection),
    CAR_ACCIDENT(R.string.rule_car_accident),
    GEOFENCING(R.string.rule_geofencing),
    SPEED_LIMIT(R.string.rule_speed_limit),
    INACTIVITY(R.string.rule_inactivity),
    PANIC_BUTTON(R.string.rule_panic_button)
}