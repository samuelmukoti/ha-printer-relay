import { PrintJob, PrintOptions } from '../models/PrintJob';

export interface ApiEndpoints {
    auth: {
        method: 'POST';
        path: '/api/auth';
        body: {
            username: string;
            password: string;
        };
        response: {
            token: string;
        };
    };

    submitJob: {
        method: 'POST';
        path: '/api/print';
        body: {
            file: Blob;
            printerName: string;
            options?: PrintOptions;
        };
        response: PrintJob;
    };
    
    getJobStatus: {
        method: 'GET';
        path: '/api/print/:jobId/status';
        params: {
            jobId: string;
        };
        response: PrintJob;
    };
    
    cancelJob: {
        method: 'DELETE';
        path: '/api/print/:jobId';
        params: {
            jobId: string;
        };
        response: void;
    };

    listPrinters: {
        method: 'GET';
        path: '/api/printers';
        response: {
            printers: Array<{
                name: string;
                status: string;
                location?: string;
                capabilities?: {
                    color?: boolean;
                    duplex?: boolean;
                    paperSizes?: string[];
                };
            }>;
        };
    };

    getQueueStatus: {
        method: 'GET';
        path: '/api/queue/status';
        response: {
            activeJobs: number;
            queuedJobs: number;
            completedJobs: number;
        };
    };
}

export interface ApiError {
    code: number;
    message: string;
    details?: unknown;
} 