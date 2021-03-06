/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.radiology.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderContext;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.radiology.DicomUtils;
import org.openmrs.module.radiology.DicomUtils.OrderRequest;
import org.openmrs.module.radiology.MwlStatus;
import org.openmrs.module.radiology.PerformedProcedureStepStatus;
import org.openmrs.module.radiology.RadiologyOrder;
import org.openmrs.module.radiology.RadiologyProperties;
import org.openmrs.module.radiology.RadiologyService;
import org.openmrs.module.radiology.ScheduledProcedureStepStatus;
import org.openmrs.module.radiology.Study;
import org.openmrs.module.radiology.db.RadiologyOrderDAO;
import org.openmrs.module.radiology.db.RadiologyReportDAO;
import org.openmrs.module.radiology.db.StudyDAO;
import org.openmrs.module.radiology.report.RadiologyReport;
import org.openmrs.module.radiology.report.RadiologyReportStatus;
import org.springframework.transaction.annotation.Transactional;

class RadiologyServiceImpl extends BaseOpenmrsService implements RadiologyService {
	
	private static final Log log = LogFactory.getLog(RadiologyServiceImpl.class);
	
	private RadiologyOrderDAO radiologyOrderDAO;
	
	private StudyDAO studyDAO;
	
	private OrderService orderService;
	
	private EncounterService encounterService;
	
	private RadiologyProperties radiologyProperties;
	
	private RadiologyReportDAO radiologyReportDAO;
	
	@Override
	public void setRadiologyOrderDao(RadiologyOrderDAO radiologyOrderDAO) {
		this.radiologyOrderDAO = radiologyOrderDAO;
	}
	
	@Override
	public void setStudyDAO(StudyDAO studyDAO) {
		this.studyDAO = studyDAO;
	}
	
	@Override
	public void setOrderService(OrderService orderService) {
		this.orderService = orderService;
	}
	
	@Override
	public void setEncounterService(EncounterService encounterService) {
		this.encounterService = encounterService;
	}
	
	@Override
	public void setRadiologyProperties(RadiologyProperties radiologyProperties) {
		this.radiologyProperties = radiologyProperties;
	}
	
	@Override
	public void setRadiologyReportDAO(RadiologyReportDAO radiologyReportDao) {
		this.radiologyReportDAO = radiologyReportDao;
	}
	
	/**
	 * @see RadiologyService#placeRadiologyOrder(RadiologyOrder)
	 */
	@Transactional
	@Override
	public RadiologyOrder placeRadiologyOrder(RadiologyOrder radiologyOrder) {
		if (radiologyOrder == null) {
			throw new IllegalArgumentException("radiologyOrder is required");
		}
		
		if (radiologyOrder.getOrderId() != null) {
			throw new IllegalArgumentException("Cannot edit an existing order!");
		}
		
		if (radiologyOrder.getStudy() == null) {
			throw new IllegalArgumentException("radiologyOrder.study is required");
		}
		
		if (radiologyOrder.getStudy().getModality() == null) {
			throw new IllegalArgumentException("radiologyOrder.study.modality is required");
		}
		
		Encounter encounter = saveRadiologyOrderEncounter(radiologyOrder.getPatient(), radiologyOrder.getOrderer(),
		    new Date());
		encounter.addOrder(radiologyOrder);
		
		OrderContext orderContext = new OrderContext();
		orderContext.setCareSetting(radiologyProperties.getRadiologyCareSetting());
		orderContext.setOrderType(radiologyProperties.getRadiologyTestOrderType());
		
		RadiologyOrder result = (RadiologyOrder) orderService.saveOrder(radiologyOrder, orderContext);
		saveStudy(result.getStudy());
		return result;
	}
	
	/**
	 * Save radiology order encounter for given parameters
	 * 
	 * @param patient the encounter patient
	 * @param provider the encounter provider
	 * @param encounterDateTime the encounter date
	 * @return radiology order encounter for given parameters
	 * @should save radiology order encounter for given parameters
	 */
	@Transactional
	private Encounter saveRadiologyOrderEncounter(Patient patient, Provider provider, Date encounterDateTime) {
		
		Encounter encounter = new Encounter();
		encounter.setPatient(patient);
		encounter.setEncounterType(radiologyProperties.getRadiologyEncounterType());
		encounter.setProvider(radiologyProperties.getOrderingProviderEncounterRole(), provider);
		encounter.setEncounterDatetime(encounterDateTime);
		
		return encounterService.saveEncounter(encounter);
	}
	
	/**
	 * <p>
	 * Save the given <code>Study</code> to the database
	 * </p>
	 * Additionally, study and study.order information are written into a DICOM xml file.
	 * 
	 * @param study study to be created or updated
	 * @return study who was created or updated
	 * @should create new study from given study object
	 * @should update existing study
	 */
	@Transactional
	private Study saveStudy(Study study) {
		
		RadiologyOrder order = study.getRadiologyOrder();
		
		if (study.getScheduledStatus() == null && order.getScheduledDate() != null) {
			study.setScheduledStatus(ScheduledProcedureStepStatus.SCHEDULED);
		}
		
		try {
			Study savedStudy = studyDAO.saveStudy(study);
			String studyInstanceUid = radiologyProperties.getStudyPrefix() + savedStudy.getStudyId();
			savedStudy.setStudyInstanceUid(studyInstanceUid);
			savedStudy = studyDAO.saveStudy(savedStudy);
			return savedStudy;
		}
		catch (Exception e) {
			log.error(e.getMessage(), e);
			log.warn("Can not save study in openmrs or dmc4che.");
		}
		return null;
	}
	
	/**
	 * @see RadiologyService#discontinueRadiologyOrder(RadiologyOrder, Provider, Date, String)
	 */
	@Transactional
	@Override
	public Order discontinueRadiologyOrder(RadiologyOrder radiologyOrderToDiscontinue, Provider orderer,
	        Date discontinueDate, String nonCodedDiscontinueReason) throws Exception {
		
		if (radiologyOrderToDiscontinue == null) {
			throw new IllegalArgumentException("radiologyOrder is required");
		}
		
		if (radiologyOrderToDiscontinue.getOrderId() == null) {
			throw new IllegalArgumentException("orderId is null");
		}
		
		if (radiologyOrderToDiscontinue.isActive() == false) {
			throw new IllegalArgumentException("order is not active");
		}
		
		if (orderer == null) {
			throw new IllegalArgumentException("provider is required");
		}
		
		Encounter encounter = saveRadiologyOrderEncounter(radiologyOrderToDiscontinue.getPatient(), orderer, discontinueDate);
		
		return orderService.discontinueOrder(radiologyOrderToDiscontinue, nonCodedDiscontinueReason, discontinueDate,
		    orderer, encounter);
	}
	
	/**
	 * @see RadiologyService#getRadiologyOrderByOrderId(Integer)
	 */
	@Transactional(readOnly = true)
	@Override
	public RadiologyOrder getRadiologyOrderByOrderId(Integer orderId) {
		if (orderId == null) {
			throw new IllegalArgumentException("orderId is required");
		}
		
		return radiologyOrderDAO.getRadiologyOrderByOrderId(orderId);
	}
	
	/**
	 * @see RadiologyService#getRadiologyOrdersByPatient(Patient)
	 */
	@Transactional(readOnly = true)
	@Override
	public List<RadiologyOrder> getRadiologyOrdersByPatient(Patient patient) {
		if (patient == null) {
			throw new IllegalArgumentException("patient is required");
		}
		
		return radiologyOrderDAO.getRadiologyOrdersByPatient(patient);
	}
	
	/**
	 * @see RadiologyService#getRadiologyOrdersByPatients(List<Patient>)
	 */
	@Transactional(readOnly = true)
	@Override
	public List<RadiologyOrder> getRadiologyOrdersByPatients(List<Patient> patients) {
		if (patients == null) {
			throw new IllegalArgumentException("patients is required");
		}
		
		return radiologyOrderDAO.getRadiologyOrdersByPatients(patients);
	}
	
	/**
	 * @see RadiologyService#updateStudyPerformedStatus(String, PerformedProcedureStepStatus)
	 */
	@Transactional
	@Override
	public Study updateStudyPerformedStatus(String studyInstanceUid, PerformedProcedureStepStatus performedStatus)
	        throws IllegalArgumentException {
		
		if (studyInstanceUid == null) {
			throw new IllegalArgumentException("studyInstanceUid is required");
		}
		
		if (performedStatus == null) {
			throw new IllegalArgumentException("performedStatus is required");
		}
		
		Study studyToBeUpdated = studyDAO.getStudyByStudyInstanceUid(studyInstanceUid);
		studyToBeUpdated.setPerformedStatus(performedStatus);
		return studyDAO.saveStudy(studyToBeUpdated);
	}
	
	@Override
	public void sendModalityWorklist(RadiologyOrder radiologyOrder, OrderRequest orderRequest) {
		MwlStatus mwlStatus = radiologyOrder.getStudy().getMwlStatus();
		String hl7blob = DicomUtils.createHL7Message(radiologyOrder, orderRequest);
		int status = DicomUtils.sendHL7Worklist(hl7blob);
		
		if (status == 1) {
			switch (orderRequest) {
				case Save_Order:
					if (mwlStatus == MwlStatus.DEFAULT || mwlStatus == MwlStatus.SAVE_ERR) {
						mwlStatus = MwlStatus.SAVE_OK;
					} else {
						mwlStatus = MwlStatus.UPDATE_OK;
					}
					break;
				case Void_Order:
					mwlStatus = MwlStatus.VOID_OK;
					break;
				case Unvoid_Order:
					mwlStatus = MwlStatus.UNVOID_OK;
					break;
				case Discontinue_Order:
					mwlStatus = MwlStatus.DISCONTINUE_OK;
					break;
				case Undiscontinue_Order:
					mwlStatus = MwlStatus.UNDISCONTINUE_OK;
					break;
				default:
					break;
				
			}
			
		} else if (status == 0) {
			switch (orderRequest) {
				case Save_Order:
					if (mwlStatus == MwlStatus.DEFAULT || mwlStatus == MwlStatus.SAVE_ERR) {
						mwlStatus = MwlStatus.SAVE_ERR;
					} else {
						mwlStatus = MwlStatus.UPDATE_ERR;
					}
					break;
				case Void_Order:
					mwlStatus = MwlStatus.VOID_ERR;
					break;
				case Unvoid_Order:
					mwlStatus = MwlStatus.UNVOID_ERR;
					break;
				case Discontinue_Order:
					mwlStatus = MwlStatus.DISCONTINUE_ERR;
					break;
				case Undiscontinue_Order:
					mwlStatus = MwlStatus.UNDISCONTINUE_ERR;
					break;
				default:
					break;
			}
		}
		radiologyOrder.getStudy().setMwlStatus(mwlStatus);
		saveStudy(radiologyOrder.getStudy());
	}
	
	/**
	 * @see RadiologyService#getStudyByStudyId(Integer)
	 */
	@Transactional(readOnly = true)
	@Override
	public Study getStudyByStudyId(Integer studyId) {
		return studyDAO.getStudyByStudyId(studyId);
	}
	
	/**
	 * @see RadiologyService#getStudyByOrderId(Integer)
	 */
	@Transactional(readOnly = true)
	@Override
	public Study getStudyByOrderId(Integer orderId) {
		if (orderId == null) {
			throw new IllegalArgumentException("orderId is required");
		}
		
		return studyDAO.getStudyByOrderId(orderId);
	}
	
	/**
	 * @see RadiologyService#getStudyByStudyInstanceUid(String)
	 */
	@Transactional(readOnly = true)
	public Study getStudyByStudyInstanceUid(String studyInstanceUid) {
		if (studyInstanceUid == null) {
			throw new IllegalArgumentException("studyInstanceUid is required");
		}
		
		return studyDAO.getStudyByStudyInstanceUid(studyInstanceUid);
	}
	
	/**
	 * @see RadiologyService#getStudiesByRadiologyOrders(List<RadiologyOrder>)
	 */
	@Override
	@Transactional(readOnly = true)
	public List<Study> getStudiesByRadiologyOrders(List<RadiologyOrder> radiologyOrders) {
		if (radiologyOrders == null) {
			throw new IllegalArgumentException("radiologyOrders are required");
		}
		
		List<Study> result = studyDAO.getStudiesByRadiologyOrders(radiologyOrders);
		return result;
	}
	
	/**
	 * @see RadiologyService#createAndClaimRadiologyReport(RadiologyOrder)
	 */
	@Transactional
	@Override
	public RadiologyReport createAndClaimRadiologyReport(RadiologyOrder radiologyOrder) throws IllegalArgumentException,
	        UnsupportedOperationException {
		if (radiologyOrder == null) {
			throw new IllegalArgumentException("radiologyOrder cannot be null");
		}
		if (radiologyOrder.getStudy() == null) {
			throw new IllegalArgumentException("study cannot be null");
		}
		if (radiologyOrder.getStudy().isCompleted() == false) {
			throw new IllegalArgumentException("cannot create RadiologyReport for uncompleted radiologyOrder");
		}
		if (radiologyReportDAO.hasRadiologyOrderCompletedRadiologyReport(radiologyOrder)) {
			throw new UnsupportedOperationException(
			        "cannot create radiologyReport for this radiologyOrder because it is already completed");
		}
		if (radiologyReportDAO.hasRadiologyOrderClaimedRadiologyReport(radiologyOrder)) {
			throw new UnsupportedOperationException(
			        "cannot create radiologyReport for this radiologyOrder because it is already claimed");
		}
		RadiologyReport radiologyReport = new RadiologyReport(radiologyOrder);
		radiologyReport.setCreator(Context.getAuthenticatedUser());
		return radiologyReportDAO.saveRadiologyReport(radiologyReport);
	}
	
	/**
	 * @see RadiologyService#saveRadiologyReport(RadiologyReport)
	 */
	@Transactional
	@Override
	public RadiologyReport saveRadiologyReport(RadiologyReport radiologyReport) throws IllegalArgumentException,
	        UnsupportedOperationException {
		if (radiologyReport == null) {
			throw new IllegalArgumentException("radiologyReport cannot be null");
		}
		if (radiologyReport.getReportStatus() == null) {
			throw new IllegalArgumentException("radiologyReportStatus cannot be null");
		}
		if (radiologyReport.getReportStatus() == RadiologyReportStatus.DISCONTINUED) {
			throw new UnsupportedOperationException("a discontinued radiologyReport cannot be saved");
		}
		if (radiologyReport.getReportStatus() == RadiologyReportStatus.COMPLETED) {
			throw new UnsupportedOperationException("a completed radiologyReport cannot be saved");
		}
		return radiologyReportDAO.saveRadiologyReport(radiologyReport);
	}
	
	/**
	 * @see RadiologyService#unclaimRadiologyReport(RadiologyReport)
	 */
	@Transactional
	@Override
	public RadiologyReport unclaimRadiologyReport(RadiologyReport radiologyReport) throws IllegalArgumentException,
	        UnsupportedOperationException {
		if (radiologyReport == null) {
			throw new IllegalArgumentException("radiologyReport cannot be null");
		}
		if (radiologyReport.getReportStatus() == null) {
			throw new IllegalArgumentException("radiologyReportStatus cannot be null");
		}
		if (radiologyReport.getReportStatus() == RadiologyReportStatus.DISCONTINUED) {
			throw new UnsupportedOperationException("a discontinued radiologyReport cannot be unclaimed");
		}
		if (radiologyReport.getReportStatus() == RadiologyReportStatus.COMPLETED) {
			throw new UnsupportedOperationException("a completed radiologyReport cannot be unclaimed");
		}
		radiologyReport.setReportStatus(RadiologyReportStatus.DISCONTINUED);
		return radiologyReportDAO.saveRadiologyReport(radiologyReport);
	}
	
	/**
	 * @see RadiologyService#completeRadiologyReport(RadiologyReport, Provider)
	 */
	@Override
	public RadiologyReport completeRadiologyReport(RadiologyReport radiologyReport, Provider principalResultsInterpreter)
	        throws IllegalArgumentException, UnsupportedOperationException {
		if (radiologyReport == null) {
			throw new IllegalArgumentException("radiologyReport cannot be null");
		}
		if (principalResultsInterpreter == null) {
			throw new IllegalArgumentException("principalResultsInterpreter cannot be null");
		}
		if (radiologyReport.getReportStatus() == null) {
			throw new IllegalArgumentException("radiologyReportStatus cannot be null");
		}
		if (radiologyReport.getReportStatus() == RadiologyReportStatus.DISCONTINUED) {
			throw new UnsupportedOperationException("a discontinued radiologyReport cannot be completed");
		}
		if (radiologyReport.getReportStatus() == RadiologyReportStatus.COMPLETED) {
			throw new UnsupportedOperationException("a completed radiologyReport cannot be completed");
		}
		radiologyReport.setReportDate(new Date());
		radiologyReport.setPrincipalResultsInterpreter(principalResultsInterpreter);
		radiologyReport.setReportStatus(RadiologyReportStatus.COMPLETED);
		return radiologyReportDAO.saveRadiologyReport(radiologyReport);
	}
	
	/**
	 * @see RadiologyService#getRadiologyReportByRadiologyReportId(Integer)
	 */
	@Transactional
	@Override
	public RadiologyReport getRadiologyReportByRadiologyReportId(Integer radiologyReportId) throws IllegalArgumentException {
		if (radiologyReportId == null) {
			throw new IllegalArgumentException("radiologyReportId cannot be null");
		}
		return radiologyReportDAO.getRadiologyReportById(radiologyReportId);
	}
	
	/**
	 * @see RadiologyService#getRadiologyReportsByRadiologyOrderAndReportStatus(RadiologyOrder,
	 *      RadiologyReportStatus)
	 */
	public List<RadiologyReport> getRadiologyReportsByRadiologyOrderAndReportStatus(RadiologyOrder radiologyOrder,
	        RadiologyReportStatus radiologyReportStatus) throws IllegalArgumentException {
		if (radiologyOrder == null) {
			throw new IllegalArgumentException("radiologyOrder cannot be null");
		}
		if (radiologyReportStatus == null) {
			throw new IllegalArgumentException("radiologyReportStatus cannot be null");
		}
		return radiologyReportDAO.getRadiologyReportsByRadiologyOrderAndRadiologyReportStatus(radiologyOrder,
		    radiologyReportStatus).size() > 0 ? radiologyReportDAO
		        .getRadiologyReportsByRadiologyOrderAndRadiologyReportStatus(radiologyOrder, radiologyReportStatus)
		        : new ArrayList<RadiologyReport>();
	}
	
	/**
	 * @see RadiologyService#hasRadiologyOrderClaimedRadiologyReport(RadiologyOrder)
	 */
	public boolean hasRadiologyOrderClaimedRadiologyReport(RadiologyOrder radiologyOrder) {
		return radiologyOrder != null ? radiologyReportDAO.hasRadiologyOrderClaimedRadiologyReport(radiologyOrder) : false;
	}
	
	/**
	 * @see RadiologyService#hasRadiologyOrderCompletedRadiologyReport(RadiologyOrder)
	 */
	public boolean hasRadiologyOrderCompletedRadiologyReport(RadiologyOrder radiologyOrder) {
		if (radiologyOrder == null) {
			throw new IllegalArgumentException("radiologyOrder cannot be null");
		}
		return radiologyReportDAO.hasRadiologyOrderCompletedRadiologyReport(radiologyOrder) ? true : false;
	}
	
	/**
	 * @see RadiologyService#getActiveRadiologyReportByRadiologyOrder(RadiologyOrder)
	 */
	public RadiologyReport getActiveRadiologyReportByRadiologyOrder(RadiologyOrder radiologyOrder) {
		if (radiologyOrder == null) {
			throw new IllegalArgumentException("radiologyOrder cannot be null");
		}
		if (hasRadiologyOrderCompletedRadiologyReport(radiologyOrder)) {
			return radiologyReportDAO.getActiveRadiologyReportByRadiologyOrder(radiologyOrder);
		}
		if (hasRadiologyOrderClaimedRadiologyReport(radiologyOrder)) {
			return radiologyReportDAO.getActiveRadiologyReportByRadiologyOrder(radiologyOrder);
		}
		return null;
	}
	
	/**
	 * @see RadiologyService#getCompletedRadiologyOrdersWithAnActiveRadiologyReport()
	 */
	@Transactional(readOnly = true)
	@Override
	public List<RadiologyOrder> getCompletedRadiologyOrdersWithAnActiveRadiologyReport() {
		return radiologyReportDAO.getCompletedRadiologyOrdersWithAnActiveRadiologyReport();
	}
	
	/**
	 * @see RadiologyService#getRadiologyReports()
	 */
	@Transactional(readOnly = true)
	@Override
	public List<RadiologyReport> getRadiologyReports() {
		return radiologyReportDAO.getRadiologyReports();
	}
}
