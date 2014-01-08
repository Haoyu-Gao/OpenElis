/*
* The contents of this file are subject to the Mozilla Public License
* Version 1.1 (the "License"); you may not use this file except in
* compliance with the License. You may obtain a copy of the License at
* http://www.mozilla.org/MPL/
*
* Software distributed under the License is distributed on an "AS IS"
* basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
* License for the specific language governing rights and limitations under
* the License.
*
* The Original Code is OpenELIS code.
*
* Copyright (C) The Minnesota Department of Health.  All Rights Reserved.
*/

package org.bahmni.feed.openelis.feed.service.impl;

import org.bahmni.feed.openelis.externalreference.dao.ExternalReferenceDao;
import org.bahmni.feed.openelis.externalreference.valueholder.ExternalReference;
import org.bahmni.openelis.domain.AccessionDetail;
import org.bahmni.openelis.domain.TestDetail;
import us.mn.state.health.lims.analysis.valueholder.Analysis;
import us.mn.state.health.lims.dictionary.dao.DictionaryDAO;
import us.mn.state.health.lims.note.dao.NoteDAO;
import us.mn.state.health.lims.note.valueholder.Note;
import us.mn.state.health.lims.panel.valueholder.Panel;
import us.mn.state.health.lims.patient.valueholder.Patient;
import us.mn.state.health.lims.person.valueholder.Person;
import us.mn.state.health.lims.result.action.util.ResultsLoadUtility;
import us.mn.state.health.lims.result.valueholder.Result;
import us.mn.state.health.lims.result.valueholder.ResultSignature;
import us.mn.state.health.lims.sample.dao.SampleDAO;
import us.mn.state.health.lims.sample.valueholder.Sample;
import us.mn.state.health.lims.samplehuman.dao.SampleHumanDAO;
import us.mn.state.health.lims.sampleitem.valueholder.SampleItem;
import us.mn.state.health.lims.statusofsample.util.StatusOfSampleUtil;
import us.mn.state.health.lims.test.valueholder.Test;
import us.mn.state.health.lims.unitofmeasure.valueholder.UnitOfMeasure;

import java.util.ArrayList;
import java.util.List;

public class AccessionService {
    private SampleDAO sampleDao;
    private SampleHumanDAO sampleHumanDAO;
    private ExternalReferenceDao externalReferenceDao;
    private NoteDAO noteDao;
    private DictionaryDAO dictionaryDAO;

    public AccessionService(SampleDAO sampleDao, SampleHumanDAO sampleHumanDAO, ExternalReferenceDao externalReferenceDao,
                            NoteDAO noteDao, DictionaryDAO dictionaryDAO) {

        this.sampleDao = sampleDao;
        this.sampleHumanDAO = sampleHumanDAO;
        this.externalReferenceDao = externalReferenceDao;
        this.noteDao = noteDao;
        this.dictionaryDAO = dictionaryDAO;
    }

    public AccessionDetail getAccessionDetailsFor(String sampleUuid) {
        Sample sample = sampleDao.getSampleByUUID(sampleUuid);
        Patient patient = sampleHumanDAO.getPatientForSample(sample);

        return mapSampleToAccessionDetails(sample, patient);
    }

    private AccessionDetail mapSampleToAccessionDetails(Sample sample, Patient patient) {
        String finalizedStatusId = getFinalizedStatus();

        Person person = patient.getPerson();

        AccessionDetail accessionDetail = new AccessionDetail();
        accessionDetail.setAccessionUuid(sample.getUUID());
        accessionDetail.setPatientUuid(patient.getUuid());
        accessionDetail.setPatientFirstName(person.getFirstName());
        accessionDetail.setPatientLastName(person.getLastName());
        //too many dates in sample table. We just picked the one that can be closest to what we need.
        accessionDetail.setDateTime(sample.getLastupdated());

        List<TestDetail> testDetails = new ArrayList<>();

        for (SampleItem sampleItem : sample.getSampleItems())
            for (Analysis analysis : sampleItem.getAnalyses()) {
                TestDetail testDetail = new TestDetail();
                testDetails.add(testDetail);
                Test test = analysis.getTest();
                testDetail.setTestName(test.getTestName());
                UnitOfMeasure unitOfMeasure = test.getUnitOfMeasure();
                if (unitOfMeasure != null) testDetail.setTestUnitOfMeasurement(unitOfMeasure.getUnitOfMeasureName());

                setExternalIds(analysis, testDetail);

                for (Result result : analysis.getResults()) {
                    if (finalizedStatusId.equals(analysis.getStatusId())) {
                        setResultDetail(testDetail, result);
                    }
                }
            }

        accessionDetail.setTestDetails(testDetails);
        return accessionDetail;
    }

    protected String getFinalizedStatus() {
        return StatusOfSampleUtil.getStatusID(StatusOfSampleUtil.AnalysisStatus.Finalized);
    }

    private void setResultDetail(TestDetail testDetail, Result result) {
        ResultSignature resultSignature = (ResultSignature) result.getResultSignatures().toArray()[0];
        testDetail.setProviderUuid(resultSignature.getSystemUser().getExternalId());
        addNotes(result.getId(), testDetail);
        testDetail.setMinNormal(result.getMinNormal());
        testDetail.setMaxNormal(result.getMaxNormal());
        testDetail.setResult(getResultValue(result));
        testDetail.setResultType(result.getResultType());
        testDetail.setIsAbnormal(result.getAbnormal());
        testDetail.setDateTime(result.getLastupdated());
    }

    private String getResultValue(Result result) {
        if (result.getResultType().equals("D"))
            return dictionaryDAO.getDataForId(result.getValue()).getDictEntry();
        else return result.getValue();
    }

    private void setExternalIds(Analysis analysis, TestDetail tr) {
        ExternalReference externalReferenceForTest = externalReferenceDao.getDataByItemId(analysis.getTest().getId(), "Test");
        tr.setTestUuid(externalReferenceForTest.getExternalId());

        Panel panel = analysis.getPanel();
        if (panel != null) {
            ExternalReference externalReferenceForPanel = externalReferenceDao.getDataByItemId(panel.getId(), "Panel");
            tr.setPanelUuid(externalReferenceForPanel.getExternalId());
        }
    }

    private void addNotes(String resultId, TestDetail testResult) {
        List<Note> notes = noteDao.getNoteByRefIAndRefTableAndSubject(resultId, getResultReferenceTableId(), "Result Note");
        for (Note note : notes) {
            testResult.addNotes(note.getText());
        }
    }

    protected String getResultReferenceTableId() {
        return ResultsLoadUtility.getResultReferenceTableId();
    }
}