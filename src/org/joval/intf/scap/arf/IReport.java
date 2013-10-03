// Copyright (C) 2012 jOVAL.org.  All rights reserved.
// This software is licensed under the AGPL 3.0 license available at http://www.joval.org/agpl_v3.txt

package org.joval.intf.scap.arf;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.xml.transform.Transformer;
import org.w3c.dom.Element;

import org.oasis.catalog.Catalog;
import scap.ai.AssetType;
import scap.arf.core.AssetReportCollection;
import scap.oval.systemcharacteristics.core.SystemInfoType;
import scap.xccdf.BenchmarkType;
import scap.xccdf.TestResultType;

import org.joval.scap.ScapException;
import org.joval.scap.arf.ArfException;
import org.joval.intf.xml.ITransformable;
import org.joval.scap.diagnostics.RuleDiagnostics;

/**
 * A representation of an ARF report collection.
 *
 * @author David A. Solin
 * @version %I% %G%
 */
public interface IReport extends ITransformable {
    /**
     * Get the asset IDs in the report.
     */
    Collection<String> getAssetIds();

    /**
     * Get a particular asset.
     */
    AssetType getAsset(String assetId) throws NoSuchElementException;

    /**
     * Get the benchmark IDs from the report requests in the report.
     */
    Collection<String> getBenchmarkIds();

    /**
     * Get a copy of a particular report request based on its benchmark ID.
     */
    BenchmarkType getBenchmark(String benchmarkId) throws ScapException;

    /**
     * Get the XCCDF result associated with the specified asset, benchmark and profile.
     */
    TestResultType getTestResult(String assetId, String benchmarkId, String profileId) throws NoSuchElementException;

    /**
     * Get diagnostic information for all the rules defined in the TestResultType corresponding to the specified asset,
     * benchmark and profile.
     */
    Collection<RuleDiagnostics> getDiagnostics(String assetId, String benchmarkId, String profileId) throws ScapException;

    /**
     * Get diagnostic information for the specified rule, in the TestResultType corresponding to the specified asset,
     * benchmark and profile.
     */
    RuleDiagnostics getDiagnostics(String assetId, String benchmarkId, String profileId, String ruleId)
	throws NoSuchElementException, ScapException;

    /**
     * Get a catalog mapping individual report IDs to the document HREFs from which the request documents originated.
     * These may be filenames or ZIP entry names if the request originated from a bundle, or component IDs if the
     * request was sourced from an SCAP datastream.
     *
     * Only an ARF report that has been generated by jOVAL XPERT will contain such a catalog.
     */
    Catalog getCatalog() throws ArfException, NoSuchElementException;

    /**
     * Just like getCatalog(), but filtered to report URIs pertaining to the specified asset.
     *
     * @see getCatalog()
     */
    Catalog getCatalog(String assetId) throws ArfException, NoSuchElementException;

    /**
     * Get the underlying JAXB type.
     */
    AssetReportCollection getAssetReportCollection();

    /**
     * Add a report request.
     *
     * @return the ID generated for the request
     */
    String addRequest(Element request);

    /**
     * Add an asset based on a SystemInfoType
     *
     * @return the ID generated for the asset
     */
    String addAsset(SystemInfoType info, Collection<String> cpes);

    /**
     * Add an XCCDF result related to the specified request and asset.
     *
     * @param requestId the request to which the report is related
     * @param assetId the asset that is the subject of the report
     * @param ref an optional URI to add to an OASIS catalog, to find the report
     * @param report the report DOM
     *
     * @return the ID generated for the report
     */
    String addReport(String requestId, String assetId, String ref, Element report) throws NoSuchElementException, ArfException;

    /**
     * Serialize the report to a file.
     */
    void writeXML(File f) throws IOException;


    /**
     * Transform using the specified template, and serialize to the specified file.
     */
    void writeTransform(Transformer transform, File output);
}
