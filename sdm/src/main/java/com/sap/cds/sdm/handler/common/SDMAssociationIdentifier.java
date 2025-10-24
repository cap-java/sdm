package com.sap.cds.sdm.handler.common;

/**
 * This record is a simple data class that holds the association name and the full entity name for
 * SDM attachment processing.
 *
 * @param associationName the association name
 * @param fullEntityName the full entity name
 */
record SDMAssociationIdentifier(String associationName, String fullEntityName) {}
