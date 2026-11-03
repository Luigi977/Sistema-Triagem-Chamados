import { useCallback, useEffect, useRef, useState } from 'react'
import { errorMessage } from '../services/api'
export function useQuery<T>(loader: (signal: AbortSignal) => Promise<T>, poll = 0) {
  const [result, setResult] = useState<{ loader: typeof loader; data?: T; error?: string }>()
  const [revision, setRevision] = useState(0)
  const current = useRef(0)
  const reload = useCallback(() => setRevision((value) => value + 1), [])
  useEffect(() => {
    const generation = ++current.current
    const controller = new AbortController()
    let busy = false
    let timer: number | undefined
    // Clear resources when the identity/query changes, retain them on safe refresh.
    const run = async () => {
      if (busy || controller.signal.aborted || (poll && document.hidden)) return
      busy = true
      try {
        const value = await loader(controller.signal)
        if (generation === current.current && !controller.signal.aborted)
          setResult({ loader, data: value })
      } catch (cause) {
        if (!controller.signal.aborted && generation === current.current)
          setResult((old) => ({
            loader,
            data: old?.loader === loader ? old.data : undefined,
            error: errorMessage(cause),
          }))
      } finally {
        busy = false
        if (poll && !controller.signal.aborted) timer = window.setTimeout(run, poll)
      }
    }
    const visible = () => {
      window.clearTimeout(timer)
      if (!document.hidden && !busy) void run()
    }
    void run()
    if (poll) document.addEventListener('visibilitychange', visible)
    return () => {
      controller.abort()
      window.clearTimeout(timer)
      document.removeEventListener('visibilitychange', visible)
    }
  }, [loader, poll, revision])
  const active = result?.loader === loader ? result : undefined
  return { data: active?.data, error: active?.error, loading: !active, reload }
}
