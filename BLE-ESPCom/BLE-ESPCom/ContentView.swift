//
//  ContentView.swift
//  BLE-ESPCom
//
//  Created by Matt Long on 11/21/25.
//

import SwiftUI

struct ContentView: View {
    @StateObject private var manager = BLEManager()
    @State private var valueInput: String = ""

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Circle()
                        .fill(statusColor)
                        .frame(width: 12, height: 12)
                    Text(manager.state.rawValue)
                        .font(.headline)
                    Spacer()
                    Button(action: manager.startScanning) {
                        Label("Scan", systemImage: "dot.radiowaves.left.and.right")
                    }
                    Button(action: manager.disconnect) {
                        Label("Disconnect", systemImage: "xmark.circle")
                    }
                }

                Text(manager.statusMessage)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                if let last = manager.lastValue {
                    Text("Last value: \(last, format: .number.grouping(.never))")
                        .font(.title3)
                        .bold()
                } else {
                    Text("No value yet.")
                        .foregroundStyle(.secondary)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Send new value")
                        .font(.headline)
                    HStack {
                        TextField("UInt32 (decimal or 0x...)", text: $valueInput)
                            .textFieldStyle(.roundedBorder)
                            .keyboardType(.numberPad)
                        Button("Write") {
                            manager.writeValue(from: valueInput)
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    Button("Read") {
                        manager.readValue()
                    }
                    .buttonStyle(.bordered)
                }

                Spacer()
            }
            .padding()
            .navigationTitle("ESP32 BLE Bridge")
        }
    }

    private var statusColor: Color {
        switch manager.state {
        case .connected:
            return .green
        case .scanning, .connecting:
            return .yellow
        case .failed:
            return .red
        default:
            return .gray
        }
    }
}

#Preview {
    ContentView()
}
