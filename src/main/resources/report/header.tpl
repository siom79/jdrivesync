<html>
<head>
    <title>jdrivesync Report 2014-10-07T11-14-27</title>
    <style type="text/css">
        table#statusTable {border-collapse: collapse;}
        table#statusTable td {border: 1px solid black;}
        thead {background-color: #339dff;}
    </style>
    <script>
function filter() {
	var select = document.getElementById("statusInput");
	var statusValue = select.options[select.options.selectedIndex].value;
	select = document.getElementById("actionInput");
	var actionValue = select.options[select.options.selectedIndex].value;
	var table = document.getElementById("statusTable");
	for (var i = 1, row; row = table.rows[i]; i++) {
		var statusDisplay = false;
		if(row.cells[1].textContent.indexOf(statusValue) != -1) {
			statusDisplay = true;
		}
		if(statusValue == "All") {
			statusDisplay = true;
		}
		var actionDisplay = false;
		if(row.cells[2].textContent.indexOf(actionValue) != -1) {
			actionDisplay = true;
		}
		if(actionValue == "All") {
			actionDisplay = true;
		}
		row.style.display = (statusDisplay && actionDisplay) ? "" : "none";
	}
}
</script>
</head>
<body>
<form name="filterForm" action="">
    <table>
        <tr>
            <td>Status:</td>
            <td>
                <select name="statusInput" id="statusInput" onchange="filter()">
                    <option value="All">All</option>
                    <option value="Synchronized">Synchronized</option>
                    <option value="Error">Error</option>
                </select>
            </td>
            <td>Action:</td>
            <td>
                <select name="actionInput" id="actionInput" onchange="filter()">
                    <option value="All">All</option>
                    <option value="Created">Created</option>
                    <option value="Deleted">Deleted</option>
                    <option value="Updated">Updated</option>
                    <option value="UpdatedMetadata">UpdatedMetadata</option>
                    <option value="Unchanged">Unchanged</option>
                    <option value="Skipped">Skipped</option>
                </select>
            </td>
        </tr>
    </table>
</form>
<table id="statusTable">
    <thead>
    <tr>
        <td>Path</td>
        <td>Status</td>
        <td>Action</td>
        <td>Direction</td>
    </tr>
    </thead>
    <tbody>