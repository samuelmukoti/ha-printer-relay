import pytest
import json
import os
import jwt
from datetime import datetime, timedelta
from unittest.mock import patch, MagicMock
from print_api import app, API_SECRET

@pytest.fixture
def client():
    app.config['TESTING'] = True
    with app.test_client() as client:
        yield client

@pytest.fixture
def auth_token():
    token = jwt.encode({
        'user': 'test',
        'exp': datetime.utcnow() + timedelta(hours=1)
    }, API_SECRET)
    return token

@pytest.fixture
def auth_headers(auth_token):
    return {'Authorization': f'Bearer {auth_token}'}

def test_health_check(client):
    response = client.get('/api/health')
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data['status'] == 'healthy'
    assert 'timestamp' in data
    assert 'version' in data

def test_auth_success(client):
    response = client.post('/api/auth', 
                         headers={'Authorization': 'Basic YWRtaW46YWRtaW4='})  # admin:admin in base64
    assert response.status_code == 200
    data = json.loads(response.data)
    assert 'token' in data

def test_auth_failure(client):
    response = client.post('/api/auth', 
                         headers={'Authorization': 'Basic aW52YWxpZDppbnZhbGlk'})  # invalid:invalid in base64
    assert response.status_code == 401

def test_missing_token(client):
    response = client.get('/api/printers')
    assert response.status_code == 401
    data = json.loads(response.data)
    assert data['error'] == 'Token is missing'

def test_invalid_token(client):
    headers = {'Authorization': 'Bearer invalid_token'}
    response = client.get('/api/printers', headers=headers)
    assert response.status_code == 401
    data = json.loads(response.data)
    assert data['error'] == 'Token is invalid'

@patch('print_api.get_printers')
def test_list_printers(mock_get_printers, client, auth_headers):
    mock_printers = [
        {'name': 'Printer1', 'status': 'idle'},
        {'name': 'Printer2', 'status': 'printing'}
    ]
    mock_get_printers.return_value = mock_printers
    
    response = client.get('/api/printers', headers=auth_headers)
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data['printers'] == mock_printers

@patch('print_api.queue_manager')
def test_submit_print_job(mock_queue_manager, client, auth_headers):
    mock_queue_manager.submit_job.return_value = 123
    
    data = {
        'printer_name': 'TestPrinter',
        'copies': '2',
        'duplex': 'true'
    }
    test_file = (open('tests/test_data/test.pdf', 'rb') if os.path.exists('tests/test_data/test.pdf')
                 else open('test_data/test.pdf', 'rb'))
    
    response = client.post(
        '/api/print',
        data={**data, 'file': (test_file, 'test.pdf')},
        headers=auth_headers,
        content_type='multipart/form-data'
    )
    
    test_file.close()
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data['job_id'] == 123
    assert data['status'] == 'submitted'

@patch('print_api.queue_manager')
def test_get_job_status(mock_queue_manager, client, auth_headers):
    mock_status = {
        'job_id': 123,
        'status': 'printing',
        'progress': 50
    }
    mock_queue_manager.get_job_status.return_value = mock_status
    
    response = client.get('/api/print/123/status', headers=auth_headers)
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data == mock_status

@patch('print_api.queue_manager')
def test_cancel_job(mock_queue_manager, client, auth_headers):
    mock_queue_manager.cancel_job.return_value = True
    
    response = client.delete('/api/print/123', headers=auth_headers)
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data['message'] == 'Job canceled successfully'

@patch('print_api.queue_manager')
def test_get_queue_status(mock_queue_manager, client, auth_headers):
    mock_status = {
        'active_jobs': 2,
        'queued_jobs': 1,
        'completed_jobs': 5
    }
    mock_queue_manager.get_queue_status.return_value = mock_status
    
    response = client.get('/api/queue/status', headers=auth_headers)
    assert response.status_code == 200
    data = json.loads(response.data)
    assert data == mock_status

def test_not_found(client):
    response = client.get('/api/nonexistent')
    assert response.status_code == 404
    data = json.loads(response.data)
    assert data['error'] == 'Not found' 