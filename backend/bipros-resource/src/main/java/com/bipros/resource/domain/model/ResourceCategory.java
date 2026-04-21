package com.bipros.resource.domain.model;

/**
 * IC-PMS M8 detailed resource category. Spans labour sub-types, equipment sub-types,
 * and material sub-types used across DMIC packages.
 */
public enum ResourceCategory {
    // Labour sub-types
    SITE_ENGINEER,
    FOREMAN,
    SKILLED_LABOUR,
    UNSKILLED_LABOUR,
    OPERATOR,
    DRIVER,
    WELDER,
    ELECTRICIAN,

    // Equipment sub-types
    EARTH_MOVING,
    CRANES_LIFTING,
    CONCRETE_EQUIPMENT,
    PAVING_EQUIPMENT,
    TRANSPORT_VEHICLES,
    PILING_RIG,
    SURVEY_EQUIPMENT,

    // Material sub-types
    CEMENT,
    STEEL_REBAR,
    AGGREGATE,
    BITUMEN,
    READY_MIX_CONCRETE,
    BRICKS_BLOCKS,
    ELECTRICAL_CABLE,
    FORMWORK,
    OTHER
}
