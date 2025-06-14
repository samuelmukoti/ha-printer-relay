#!/usr/bin/env python3
import cups
import json
import logging
import os
import time
from typing import Dict, List, Optional
from dataclasses import dataclass
from datetime import datetime

@dataclass
class PrintJob:
    job_id: int
    printer_name: str
    status: str
    created_at: datetime
    completed_at: Optional[datetime] = None
    error_message: Optional[str] = None

class JobQueueManager:
    def __init__(self, cups_connection: Optional[cups.Connection] = None):
        self.conn = cups_connection or cups.Connection()
        self.jobs: Dict[int, PrintJob] = {}
        self.logger = logging.getLogger("job_queue_manager")
        
    def submit_job(self, printer_name: str, filename: str, options: Dict = None) -> int:
        """Submit a new print job to the specified printer."""
        try:
            job_id = self.conn.printFile(printer_name, filename, "Print Job", options or {})
            self.jobs[job_id] = PrintJob(
                job_id=job_id,
                printer_name=printer_name,
                status="pending",
                created_at=datetime.now()
            )
            self.logger.info(f"Submitted job {job_id} to printer {printer_name}")
            return job_id
        except cups.IPPError as e:
            self.logger.error(f"Failed to submit job to {printer_name}: {str(e)}")
            raise

    def get_job_status(self, job_id: int) -> Dict:
        """Get the current status of a print job."""
        try:
            job = self.jobs.get(job_id)
            if not job:
                return {"error": "Job not found"}
            
            cups_job = self.conn.getJobs().get(job_id)
            if cups_job:
                status = cups_job["job-state"]
                state_reasons = cups_job.get("job-state-reasons", [])
                
                # Update job status
                if status == 3:  # IPP_JOB_PENDING
                    job.status = "pending"
                elif status == 4:  # IPP_JOB_HELD
                    job.status = "held"
                elif status == 5:  # IPP_JOB_PROCESSING
                    job.status = "printing"
                elif status == 6:  # IPP_JOB_STOPPED
                    job.status = "stopped"
                elif status == 7:  # IPP_JOB_CANCELED
                    job.status = "canceled"
                elif status == 8:  # IPP_JOB_ABORTED
                    job.status = "aborted"
                elif status == 9:  # IPP_JOB_COMPLETED
                    job.status = "completed"
                    if not job.completed_at:
                        job.completed_at = datetime.now()
                
                return {
                    "job_id": job.job_id,
                    "printer_name": job.printer_name,
                    "status": job.status,
                    "created_at": job.created_at.isoformat(),
                    "completed_at": job.completed_at.isoformat() if job.completed_at else None,
                    "state_reasons": state_reasons
                }
            else:
                # Job no longer in CUPS queue, must have been completed or removed
                if job.status not in ["completed", "canceled", "aborted"]:
                    job.status = "completed"
                    job.completed_at = datetime.now()
                
                return {
                    "job_id": job.job_id,
                    "printer_name": job.printer_name,
                    "status": job.status,
                    "created_at": job.created_at.isoformat(),
                    "completed_at": job.completed_at.isoformat() if job.completed_at else None
                }
                
        except cups.IPPError as e:
            self.logger.error(f"Failed to get status for job {job_id}: {str(e)}")
            return {"error": str(e)}

    def cancel_job(self, job_id: int) -> bool:
        """Cancel a print job."""
        try:
            self.conn.cancelJob(job_id)
            job = self.jobs.get(job_id)
            if job:
                job.status = "canceled"
            self.logger.info(f"Canceled job {job_id}")
            return True
        except cups.IPPError as e:
            self.logger.error(f"Failed to cancel job {job_id}: {str(e)}")
            return False

    def clean_old_jobs(self, max_age_hours: int = 24) -> None:
        """Clean up old completed jobs from memory."""
        now = datetime.now()
        to_remove = []
        for job_id, job in self.jobs.items():
            if job.completed_at:
                age = (now - job.completed_at).total_seconds() / 3600
                if age > max_age_hours:
                    to_remove.append(job_id)
        
        for job_id in to_remove:
            del self.jobs[job_id]
        
        if to_remove:
            self.logger.info(f"Cleaned up {len(to_remove)} old jobs")

    def get_queue_status(self) -> Dict:
        """Get overall queue status."""
        active_jobs = self.conn.getJobs()
        return {
            "total_jobs": len(self.jobs),
            "active_jobs": len(active_jobs),
            "queued_jobs": len([j for j in self.jobs.values() if j.status == "pending"]),
            "completed_jobs": len([j for j in self.jobs.values() if j.status == "completed"]),
            "failed_jobs": len([j for j in self.jobs.values() if j.status in ["aborted", "canceled"]])
        }

# Global instance for use by other modules
queue_manager = JobQueueManager() 