#!/usr/bin/env python3
import unittest
from unittest.mock import Mock, patch
from datetime import datetime, timedelta
import cups
from job_queue_manager import JobQueueManager, PrintJob

class TestJobQueueManager(unittest.TestCase):
    def setUp(self):
        self.mock_cups = Mock(spec=cups.Connection)
        self.queue_manager = JobQueueManager(self.mock_cups)

    def test_submit_job(self):
        # Setup
        self.mock_cups.printFile.return_value = 123
        printer_name = "test_printer"
        filename = "test.pdf"
        options = {"copies": 2}

        # Execute
        job_id = self.queue_manager.submit_job(printer_name, filename, options)

        # Assert
        self.assertEqual(job_id, 123)
        self.mock_cups.printFile.assert_called_once_with(printer_name, filename, "Print Job", options)
        self.assertIn(123, self.queue_manager.jobs)
        self.assertEqual(self.queue_manager.jobs[123].printer_name, printer_name)
        self.assertEqual(self.queue_manager.jobs[123].status, "pending")

    def test_submit_job_error(self):
        # Setup
        self.mock_cups.printFile.side_effect = cups.IPPError("Failed to print")

        # Execute & Assert
        with self.assertRaises(cups.IPPError):
            self.queue_manager.submit_job("test_printer", "test.pdf")

    def test_get_job_status_pending(self):
        # Setup
        job_id = 123
        self.queue_manager.jobs[job_id] = PrintJob(
            job_id=job_id,
            printer_name="test_printer",
            status="pending",
            created_at=datetime.now()
        )
        self.mock_cups.getJobs.return_value = {
            job_id: {"job-state": 3, "job-state-reasons": ["none"]}
        }

        # Execute
        status = self.queue_manager.get_job_status(job_id)

        # Assert
        self.assertEqual(status["status"], "pending")
        self.assertEqual(status["printer_name"], "test_printer")
        self.assertIn("created_at", status)
        self.assertIsNone(status["completed_at"])

    def test_get_job_status_completed(self):
        # Setup
        job_id = 123
        self.queue_manager.jobs[job_id] = PrintJob(
            job_id=job_id,
            printer_name="test_printer",
            status="printing",
            created_at=datetime.now()
        )
        self.mock_cups.getJobs.return_value = {
            job_id: {"job-state": 9, "job-state-reasons": ["processing-completed"]}
        }

        # Execute
        status = self.queue_manager.get_job_status(job_id)

        # Assert
        self.assertEqual(status["status"], "completed")
        self.assertIsNotNone(status["completed_at"])

    def test_get_job_status_not_found(self):
        # Execute
        status = self.queue_manager.get_job_status(999)

        # Assert
        self.assertIn("error", status)
        self.assertEqual(status["error"], "Job not found")

    def test_cancel_job(self):
        # Setup
        job_id = 123
        self.queue_manager.jobs[job_id] = PrintJob(
            job_id=job_id,
            printer_name="test_printer",
            status="printing",
            created_at=datetime.now()
        )

        # Execute
        result = self.queue_manager.cancel_job(job_id)

        # Assert
        self.assertTrue(result)
        self.mock_cups.cancelJob.assert_called_once_with(job_id)
        self.assertEqual(self.queue_manager.jobs[job_id].status, "canceled")

    def test_cancel_job_error(self):
        # Setup
        self.mock_cups.cancelJob.side_effect = cups.IPPError("Failed to cancel")

        # Execute
        result = self.queue_manager.cancel_job(123)

        # Assert
        self.assertFalse(result)

    def test_clean_old_jobs(self):
        # Setup
        now = datetime.now()
        old_job = PrintJob(
            job_id=1,
            printer_name="test_printer",
            status="completed",
            created_at=now - timedelta(hours=25),
            completed_at=now - timedelta(hours=25)
        )
        recent_job = PrintJob(
            job_id=2,
            printer_name="test_printer",
            status="completed",
            created_at=now - timedelta(hours=12),
            completed_at=now - timedelta(hours=12)
        )
        self.queue_manager.jobs = {1: old_job, 2: recent_job}

        # Execute
        self.queue_manager.clean_old_jobs(max_age_hours=24)

        # Assert
        self.assertNotIn(1, self.queue_manager.jobs)
        self.assertIn(2, self.queue_manager.jobs)

    def test_get_queue_status(self):
        # Setup
        self.queue_manager.jobs = {
            1: PrintJob(job_id=1, printer_name="p1", status="completed", created_at=datetime.now()),
            2: PrintJob(job_id=2, printer_name="p1", status="pending", created_at=datetime.now()),
            3: PrintJob(job_id=3, printer_name="p2", status="aborted", created_at=datetime.now())
        }
        self.mock_cups.getJobs.return_value = {2: {}}

        # Execute
        status = self.queue_manager.get_queue_status()

        # Assert
        self.assertEqual(status["total_jobs"], 3)
        self.assertEqual(status["active_jobs"], 1)
        self.assertEqual(status["queued_jobs"], 1)
        self.assertEqual(status["completed_jobs"], 1)
        self.assertEqual(status["failed_jobs"], 1)

if __name__ == "__main__":
    unittest.main() 