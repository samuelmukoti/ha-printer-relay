import pytest
import json
import os
from unittest.mock import patch, MagicMock

# Note: conftest.py sets up the test environment before any imports
from print_api import app

@pytest.fixture
def client():
    app.config['TESTING'] = True
    with app.test_client() as client:
        yield client

@pytest.fixture
def auth_client():
    """Client that simulates requests coming from HA Ingress (authenticated)."""
    app.config['TESTING'] = True
    with app.test_client() as client:
        # Simulate Ingress by adding the X-Ingress-Path header
        client.environ_base['HTTP_X_INGRESS_PATH'] = '/api/hassio_ingress/test'
        yield client

def test_health_check(client):
    """Test health check endpoint."""
    response = client.get('/api/health')
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data['status'] == 'healthy'
    assert 'timestamp' in data
    assert 'version' in data

@patch('print_api.get_printers')
def test_list_printers(mock_get_printers, auth_client):
    """Test listing available printers."""
    mock_printers = [
        {'name': 'Printer1', 'status': 'idle'},
        {'name': 'Printer2', 'status': 'printing'}
    ]
    mock_get_printers.return_value = mock_printers

    response = auth_client.get('/api/printers')
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data['printers'] == mock_printers

@patch('job_queue_manager.get_queue_manager')
def test_submit_print_job(mock_get_queue_manager, auth_client):
    """Test submitting a print job."""
    mock_queue_manager = MagicMock()
    mock_get_queue_manager.return_value = mock_queue_manager
    mock_queue_manager.submit_job.return_value = 123

    data = {
        'printer_name': 'TestPrinter',
        'copies': '2',
        'duplex': 'true'
    }
    test_file = (open('tests/test_data/test.pdf', 'rb') if os.path.exists('tests/test_data/test.pdf')
                 else open('test_data/test.pdf', 'rb'))

    response = auth_client.post(
        '/api/print',
        data={**data, 'file': (test_file, 'test.pdf')},
        content_type='multipart/form-data'
    )

    test_file.close()
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data['job_id'] == 123
    assert data['status'] == 'submitted'

@patch('job_queue_manager.get_queue_manager')
def test_get_job_status(mock_get_queue_manager, auth_client):
    """Test getting job status."""
    mock_queue_manager = MagicMock()
    mock_get_queue_manager.return_value = mock_queue_manager
    mock_status = {
        'job_id': 123,
        'status': 'printing',
        'progress': 50
    }
    mock_queue_manager.get_job_status.return_value = mock_status

    response = auth_client.get('/api/print/123/status')
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data == mock_status

@patch('job_queue_manager.get_queue_manager')
def test_cancel_job(mock_get_queue_manager, auth_client):
    """Test canceling a print job."""
    mock_queue_manager = MagicMock()
    mock_get_queue_manager.return_value = mock_queue_manager
    mock_queue_manager.cancel_job.return_value = True

    response = auth_client.delete('/api/print/123')
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data['message'] == 'Job canceled successfully'

@patch('job_queue_manager.get_queue_manager')
def test_get_queue_status(mock_get_queue_manager, auth_client):
    """Test getting queue status."""
    mock_queue_manager = MagicMock()
    mock_get_queue_manager.return_value = mock_queue_manager
    mock_status = {
        'active_jobs': 2,
        'queued_jobs': 1,
        'completed_jobs': 5
    }
    mock_queue_manager.get_queue_status.return_value = mock_status

    response = auth_client.get('/api/queue/status')
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data == mock_status

def test_not_found(client):
    """Test 404 handler."""
    response = client.get('/api/nonexistent')
    assert response.status_code == 404
    data = json.loads(response.data)
    assert data['error'] == 'Not found'

def test_submit_print_job_no_file(auth_client):
    """Test submitting without a file."""
    response = auth_client.post('/api/print', data={'printer_name': 'TestPrinter'})
    assert response.status_code == 400
    data = json.loads(response.data)
    assert 'No file' in data['error']

def test_submit_print_job_no_printer(auth_client):
    """Test submitting without specifying printer."""
    test_file = (open('tests/test_data/test.pdf', 'rb') if os.path.exists('tests/test_data/test.pdf')
                 else open('test_data/test.pdf', 'rb'))

    response = auth_client.post(
        '/api/print',
        data={'file': (test_file, 'test.pdf')},
        content_type='multipart/form-data'
    )
    test_file.close()

    assert response.status_code == 400
    data = json.loads(response.data)
    assert 'Printer name' in data['error']


def test_unauthenticated_request(client):
    """Test that unauthenticated requests are rejected."""
    response = client.get('/api/printers')
    assert response.status_code == 401
    data = json.loads(response.data)
    assert 'error' in data
