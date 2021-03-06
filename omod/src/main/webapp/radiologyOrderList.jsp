<%@ include file="/WEB-INF/template/include.jsp"%>

<openmrs:require privilege="View Orders" otherwise="/login.htm"
	redirect="/module/radiology/radiologyOrderList.jsp" />

<%@ include file="/WEB-INF/template/header.jsp"%>
<%@ include file="./localHeader.jsp"%>

<openmrs:htmlInclude file="/scripts/calendar/calendar.js" />
<openmrs:htmlInclude
	file="/scripts/jquery-ui/js/jquery-ui-1.7.2.custom.min.js" />
<openmrs:htmlInclude file="/moduleResources/radiology/radiology.css" />
<openmrs:htmlInclude
	file="/moduleResources/radiology/js/jquery.dataTables.min.js" />

<%@ include
	file="/WEB-INF/view/module/radiology/resources/js/orderList.js"%>
<openmrs:htmlInclude file="/moduleResources/radiology/js/sortNumbers.js" />
<openmrs:htmlInclude
	file="/moduleResources/radiology/css/jquery.dataTables.min.css" />
<openmrs:htmlInclude
	file="/moduleResources/radiology/css/details-control.dataTables.css" />

<%@ include
	file="/WEB-INF/view/module/radiology/resources/js/orderList.js"%>


<div id="calendar"></div>
<div id="voidReasonPopup">
	<label><spring:message code="general.voidReason" />:</label><br /> <input
		name="voidReason" type="text" style="width: 20em" /><br /> <input
		id="voidOrderButton" type="button"
		value='<spring:message code="general.void"/>' />
</div>

<h2>
	<spring:message code="radiology.manageOrders" />
</h2>
<div id="openmrs_msg" name="loading">
	<spring:message code="general.loading" />
</div>
<openmrs:hasPrivilege privilege="Add Orders">
	<a href="radiologyOrder.form"><spring:message
			code="radiology.addOrder" /></a>
	<br />
</openmrs:hasPrivilege>
<br />
<span class="boxHeader"> <b><spring:message
			code="radiology.worklist" /></b> <a id="clearResults" href="#"
	style="float: right"> <spring:message code="radiology.clearResults" />
</a>
</span>
<div class="box">
	<table id="searchForm" cellspacing="10">
		<tr>
			<form id="orderList">
				<td><label><spring:message code="radiology.patient" />:</label>
					<input name="patientQuery" type="text" style="width: 20em"
					title="<spring:message
						code="radiology.minChars" />" /></td>
				<td><label><spring:message code="radiology.startDate" />:</label>
					<input name="startDate" type="text" onclick="showCalendar(this)" /></td>
				<td><label><spring:message code="radiology.endDate" />:</label>
					<input name="endDate" type="text" onclick="showCalendar(this)" /></td>
				<td><select id="selectSortType">
						<option value="allOrders"><spring:message
								code="radiology.showAllOrders" /></option>
						<option value="completedOrdersWithNoReport"><spring:message
								code="radiology.showOrdersWithoutAReport" /></option>
						<option value="completedOrdersWithAReport"><spring:message
								code="radiology.showOrdersWithAReport" /></option>
				</select></td>
				<td><input id="findButton" type="button"
					value="<spring:message code="radiology.find"/>" /></td>
				<td id="errorSpan"></td>
			</form>
		</tr>
	</table>
	<br>
	<div id="results"></div>

</div>
<br />
<%@ include file="/WEB-INF/template/footer.jsp"%>
