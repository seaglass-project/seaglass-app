<?php
require_once '.creds.php';

$db = mysqli_connect($MYSQL_HOSTNAME, $MYSQL_USERNAME,
                    $MYSQL_PASSWORD, $MYSQL_DATABASE);

if ($db->connect_error) {
    die("Cannot connect to the database");
}

if ($_SERVER["CONTENT_TYPE"] != "application/json; charset=utf-8") {
    die("Unexpected or missing Content-Type");
}

$data = json_decode(file_get_contents('php://input'), true);

if ($data['version'] != 1) {
    die("Unexpected or missing JSON version");
}

$uuidRegEx = "/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i";

if (!preg_match($uuidRegEx, $data['uuid'])) {
    die("Bad UUID");
}

if (!preg_match($uuidRegEx, $data['uploadkey'])) {
    die("Bad upload key");
}

$stmt = $db->prepare("SELECT uploadkey FROM UploadKeys WHERE uuid = ?");
$stmt->bind_param("s", $data['uuid']);
$stmt->execute();
if ($stmt->num_rows > 0) {
    $stmt->bind_result($uploadkey);
    if (strcasecmp($data['uploadkey'], $uploadkey)) {
        die("Bad upload key");
    }
} else {
    $stmt->close();
    $stmt = $db->prepare("INSERT INTO UploadKeys VALUES(?, ?)");
    $stmt->bind_param("ss", $data['uuid'], $data['uploadkey']);
    $stmt->execute();
}
$stmt->close();

$stmt = $db->prepare("INSERT INTO CellObservation VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
$stmt->bind_param("siiissssssiiii", $data['uuid'], $id, $bsic, $ta, $si1, $si2,
    $si2quat, $si3, $si4, $si13, $timestamp, $band, $arfcn, $dBm);

foreach ($data['cellObservations'] as $cellObservation) {
    $id = $cellObservation['id'];
    $bsic = $cellObservation['bsic'];
    $ta = $cellObservation['ta'];
    if ($ta == -1) {
        $ta = null;
    }
    $si1 = base64_decode($cellObservation['si1']);
    $si2 = base64_decode($cellObservation['si2']);
    $si2quat = base64_decode($cellObservation['si2quat']);
    $si3 = base64_decode($cellObservation['si3']);
    $si4 = base64_decode($cellObservation['si4']);
    $si13 = base64_decode($cellObservation['si13']);
    $timestamp = $cellObservation['measurementHeader']['timestamp'];
    $band = $cellObservation['measurementHeader']['band'];
    $arfcn = $cellObservation['measurementHeader']['arfcn'];
    $dBm = $cellObservation['measurementHeader']['dBm'];
    $stmt->execute();
}

$stmt->close();

$stmt = $db->prepare("INSERT INTO GSMPacket VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
$stmt->bind_param("siiiiisiiii", $data['uuid'], $id, $type, $subtype,
    $timeslot, $frameNumber, $payload, $timestamp, $band, $arfcn, $dBm);

foreach ($data['gsmPackets'] as $gsmPacket) {
    $id = $gsmPacket['id'];
    $type = $gsmPacket['type'];
    $subtype = $gsmPacket['subtype'];
    $timeslot = $gsmPacket['timeslot'];
    $frameNumber = $gsmPacket['frameNumber'];
    $payload = $gsmPacket['payload'];
    $timestamp = $gsmPacket['measurementHeader']['timestamp'];
    $band = $gsmPacket['measurementHeader']['band'];
    $arfcn = $gsmPacket['measurementHeader']['arfcn'];
    $dBm = $gsmPacket['measurementHeader']['dBm'];
    $stmt->execute();
}

$stmt->close();

$stmt = $db->prepare("INSERT INTO SpectrumMeasurement VALUES(?, ?, ?, ?, ?, ?)");
$stmt->bind_param("siiiii", $data['uuid'], $id, $timestamp, $band, $arfcn, $dBm);

foreach ($data['spectrumMeasurements'] as $spectrumMeasurement) {
    $id = $spectrumMeasurement['id'];
    $timestamp = $spectrumMeasurement['measurementHeader']['timestamp'];
    $band = $spectrumMeasurement['measurementHeader']['band'];
    $arfcn = $spectrumMeasurement['measurementHeader']['arfcn'];
    $dBm = $spectrumMeasurement['measurementHeader']['dBm'];
    $stmt->execute();
}

$stmt = $db->prepare("INSERT INTO LocationMeasurement VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)");
$stmt->bind_param("siidddddd", $data['uuid'], $id, $timestamp, $latitude, $longitude,
    $altitude, $bearing, $speed, $horizontalAccuracy);

foreach ($data['locationMeasurements'] as $locationMeasurement) {
    $id = $locationMeasurement['id'];
    $timestamp = $locationMeasurement['timestamp'];
    $latitude = $locationMeasurement['latitude'];
    $longitude = $locationMeasurement['longitude'];
    $altitude = $locationMeasurement['altitude'];
    $bearing = $locationMeasurement['bearing'];
    $speed = $locationMeasurement['speed'];
    $horizontalAccuracy = $locationMeasurement['horizontalAccuracy'];
    $stmt->execute();
}

$stmt->close();

echo "OK";
?>