export interface PrintJob {
    id: string;
    status: PrintJobStatus;
    printerName: string;
    createdAt: string;
    progress?: number;
    errorMessage?: string;
    options?: PrintOptions;
}

export enum PrintJobStatus {
    QUEUED = 'queued',
    PRINTING = 'printing',
    COMPLETED = 'completed',
    FAILED = 'failed',
    CANCELLED = 'cancelled'
}

export interface PrintOptions {
    copies?: number;
    duplex?: boolean;
    quality?: PrintQuality;
    paperSize?: string;
    orientation?: PrintOrientation;
}

export enum PrintQuality {
    DRAFT = 'draft',
    NORMAL = 'normal',
    HIGH = 'high'
}

export enum PrintOrientation {
    PORTRAIT = 'portrait',
    LANDSCAPE = 'landscape'
} 